package com.claro.compiler_backends.java_source.monomorphization;

import com.claro.compiler_backends.java_source.monomorphization.ipc_coordinator.SubprocessRegistration;
import com.claro.compiler_backends.java_source.monomorphization.proto.ipc_protos.IPCMessages;
import com.claro.compiler_backends.java_source.monomorphization.proto.ipc_protos.IPCMessages.MonomorphizationRequest;
import com.claro.intermediate_representation.types.Types;
import com.claro.runtime_utilities.ClaroRuntimeUtilities;
import com.claro.runtime_utilities.http.$ClaroHttpServer;
import com.claro.runtime_utilities.http.$HttpUtil;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$ipc$main_compilation_unit_monomorphization_ipc.DepModuleMonomorphizationService;
import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$ipc$main_compilation_unit_monomorphization_ipc.sendMessageToSubprocess_TriggerMonomorphization;
import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$ipc_coordinator$monomorphization_ipc_coordinator.getDepModuleCoordinatorServerForFreePort;
import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$ipc_coordinator$monomorphization_ipc_coordinator.startCoordinatorServerAndAwaitShutdown;

// This class contains some quite complex subprocess orchestration logic that should be hidden from the compiler logic
// itself. Hence the only publicly exposed function here is the single getDepModuleMonomorphization() function that
// should be called to opaquely retrieve the monomorphization for an arbitrary dep module monomorphization request.
// Unless you're actually working on modifying the dep module monomorphization subprocess coordination architecture, you
// really shouldn't bother spending too much effort fully grocking the multiprocessing that's actually going on under
// the hood here.
@AutoBazelRepository
public class MonomorphizationCoordinator {

  private static int coordinatorPort = -1;
  private static $ClaroHttpServer coordinatorServer = null;
  // This cache enables us to ensure that we don't make IPC calls for monomorphizations that have already been retrieved.
  // This will also be consumed after all dep module monomorphizations are completed so that they can all be included in
  // this compilation unit's codegen.
  public static final HashBasedTable<String, MonomorphizationRequest, String>
      monomorphizationsByModuleAndRequestCache = HashBasedTable.create();

  public static ImmutableMap<String, String> DEP_GRAPH_CLARO_MODULE_PATHS_BY_UNIQUE_MODULE_NAME;
  public static Map<String, String> RUNFILES_ENV_VARS;

  // Static iniitialization happening here b/c preloading Bazel's runfiles is notably an expensive operation that should
  // only be done once.
  private static final String DEP_MODULE_MONOMORPHIZATION_SUBPROCESS_BINARY_PATH;

  static {
    try {
      Runfiles r;
      DEP_MODULE_MONOMORPHIZATION_SUBPROCESS_BINARY_PATH =
          (r = Runfiles
              .preload()
              .withSourceRepository(AutoBazelRepository_MonomorphizationCoordinator.NAME))
              .rlocation(
                  "claro-lang/src/java/com/claro/compiler_backends/java_source/monomorphization/dep_module_monomorphization_deploy.jar");
      RUNFILES_ENV_VARS = r.getEnvVars();
    } catch (IOException e) {
      throw new RuntimeException("Internal Compiler Error! Failed to preload Runfiles", e);
    }
  }

  // TODO(steving) In the future, assuming this "simple" implementation works, I should actually be able to potentially
  //   optimize this process via some parallelization via some clever scheme to immediately trigger monomorphization the
  //   second it's recognized during type checking. This would give the subprocesses a head start of starting up earlier
  //   than they do in the current approach where they don't startup until codegen. And since they don't necessarily
  //   depend on each other, they could actually be running in parallel rather than a single subprocess actively
  //   processing MonomorphizationRequests at any given time. This could potentially help to alleviate some of the
  //   overhead of Dep Module Monomorphization, so I should circle back to this when I get a chance. (Impl note: I think
  //   it'd center around a Caffeine loading cache w/ infinite timeout so that multiple threads could be updating
  //   essentially a central work registry safely; triggering monomorphization would just be calling get() on the cache).
  public static void getDepModuleMonomorphization(String module, MonomorphizationRequest depModuleMonomorphizationReq) {
    // See if the IPC req can be skipped in the case that this monomorphization has already been retrieved previously.
    if (monomorphizationsByModuleAndRequestCache.get(module, depModuleMonomorphizationReq) != null) {
      return;
    }

    try {
      // Make a blocking IPC call to the dep module monomorphization subprocess.
      IPCMessages.MonomorphizationResponse monomorphizationRes =
          IPCMessages.MonomorphizationResponse.parseFrom(
              BaseEncoding.base64Url().decode(
                  Futures.transform(
                      getDepModuleMonomorphizationSubprocessClient(module),
                      depModuleMonomorphizationService ->
                          sendMessageToSubprocess_TriggerMonomorphization.apply(
                              depModuleMonomorphizationService,
                              BaseEncoding.base64Url().encode(depModuleMonomorphizationReq.toByteArray())
                          ).get(),
                      MoreExecutors.directExecutor()
                  ).get()));
      if (!monomorphizationRes.getOptionalErrorMessage().isEmpty()) {
        shutdownDepModuleMonomorphization();
        throw new RuntimeException(
            "Internal Compiler Error! Dep Module Monomorphization Failed for Module: " + module
            + " for current MonomorphizationRequest:\n" + depModuleMonomorphizationReq
            + "\nHere's the stacktrace from the dep module subprocess:\n" +
            monomorphizationRes.getOptionalErrorMessage());
      }
      // Store all local monomorphizations returned by the dep module subprocess, they'll need to be included in the
      // module's codegen.
      for (IPCMessages.MonomorphizationResponse.Monomorphization monomorphization : monomorphizationRes.getLocalModuleMonomorphizationsList()) {
        // Cache the result so that we can avoid duplicate calls in the future.
        monomorphizationsByModuleAndRequestCache.put(
            module, monomorphization.getMonomorphizationRequest(), monomorphization.getMonomorphizationCodegen());
        // Update the set of referenced types that will need a custom Java class codegen'd for them.
        monomorphizationRes.getReferencedTypesToCodegenCustomClassesForList().forEach(
            t -> {
              Types.StructType structType = (Types.StructType) Types.parseTypeProto(t.getType());
              structType.autoValueIgnored_concreteTypeMappings =
                  Optional.of(t.getConcreteTypeMappingsList().stream().collect(ImmutableMap.toImmutableMap(
                      m -> Types.parseTypeProto(m.getGenericType()),
                      m -> Types.parseTypeProto(m.getConcreteType())
                  )));
              Types.StructType.allReferencedConcreteStructTypesToOptionalGenericTypeMappings.put(
                  t.getCustomClassName(),
                  structType
              );
            }
        );
      }
      // Handle any transitive dep module monomorphizations that were requested by the dep module subprocess.
      for (IPCMessages.MonomorphizationResponse.TransitiveDepModuleMonomorphizationRequest
          transitiveDepModuleMonomorphizationReq : monomorphizationRes.getTransitiveDepModuleMonomorphizationRequestsList()) {
        // I want to have the current module's compilation process move in as much of a straight line as possible, so
        // for now, I'm going to immediately request the monomorphization from this transitive dep module.
        getDepModuleMonomorphization(
            transitiveDepModuleMonomorphizationReq.getUniqueModuleName(),
            transitiveDepModuleMonomorphizationReq.getMonomorphizationRequest()
        );
      }
    } catch (InterruptedException | ExecutionException e) {
      shutdownDepModuleMonomorphization();
      throw new RuntimeException("Internal Compiler Error! Failed to get dep module monomorphization from subprocess.", e);
    } catch (InvalidProtocolBufferException | IllegalArgumentException e) {
      shutdownDepModuleMonomorphization();
      throw new RuntimeException("Internal Compiler Error! Failed to parse MonomorphizationResponse proto.", e);
    }
  }

  private static HashMap<String, SubprocessRegistration.DepModuleMonomorphizationSubprocessState>
  getRegisteredMonomorphizationSubprocessesByUniqueModuleNameAndStartLocalCoordinatorServerIfNecessary() {
    if (SubprocessRegistration.registeredMonomorphizationSubprocessesByUniqueModuleName.isDone()) {
      try {
        return SubprocessRegistration.registeredMonomorphizationSubprocessesByUniqueModuleName.get();
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException("Should be unreachable.");
      }
    }
    // In this case I need to start up the local coordinator server and then I can mark the registry ready.
    startCoordinatorServer();
    HashMap<String, SubprocessRegistration.DepModuleMonomorphizationSubprocessState> registry = Maps.newHashMap();
    SubprocessRegistration.registeredMonomorphizationSubprocessesByUniqueModuleName.set(registry);
    return registry;
  }

  private static void startCoordinatorServer() {
    // Startup the coordinator server so it's ready to communicate.
    coordinatorServer = getDepModuleCoordinatorServerForFreePort.apply();
    coordinatorPort = coordinatorServer.server.getListenAddresses().get(0).getPort();
    $ClaroHttpServer.silent = true;
    new Thread(() -> {
      startCoordinatorServerAndAwaitShutdown.apply(coordinatorServer, "COORDINATOR STARTED!");

      // Cleanup once something triggers shutdown. W/o this the process would hang forever as these threads are
      // going to live forever.
      ClaroRuntimeUtilities.$shutdownAndAwaitTermination(ClaroRuntimeUtilities.DEFAULT_EXECUTOR_SERVICE);
      $HttpUtil.shutdownOkHttpClient();
    }).start();
  }

  private static ListenableFuture<DepModuleMonomorphizationService> getDepModuleMonomorphizationSubprocessClient(
      String uniqueModuleName) {
    // If a subprocess has already been triggered for this dep module, then I can just return the future client that'll
    // be marked ready once it can respond to monomorphization requests.
    HashMap<String, SubprocessRegistration.DepModuleMonomorphizationSubprocessState> registry =
        getRegisteredMonomorphizationSubprocessesByUniqueModuleNameAndStartLocalCoordinatorServerIfNecessary();
    if (!registry.containsKey(uniqueModuleName)) {
      // Otherwise, we'll need to actually trigger the subprocess for the dep module and register it.
      registry.put(uniqueModuleName, SubprocessRegistration.DepModuleMonomorphizationSubprocessState.create());
      try {
        if (!Files.exists(Paths.get(DEP_MODULE_MONOMORPHIZATION_SUBPROCESS_BINARY_PATH))) {
          MonomorphizationCoordinator.shutdownDepModuleMonomorphization();
          throw new RuntimeException(
              "Internal Compiler Error! Dep Module Monomorphization Subprocess binary not found at: " +
              DEP_MODULE_MONOMORPHIZATION_SUBPROCESS_BINARY_PATH);
        }
        ProcessBuilder depModuleSubprocess =
            new ProcessBuilder()
                .command("java", "-jar", DEP_MODULE_MONOMORPHIZATION_SUBPROCESS_BINARY_PATH,
                         "--coordinator_port", String.valueOf(coordinatorPort),
                         "--dep_module_file_path", DEP_GRAPH_CLARO_MODULE_PATHS_BY_UNIQUE_MODULE_NAME.get(uniqueModuleName),
                         // TODO(steving) DELETE THIS, The uniqe name should be looked up in the .claro_module.
                         "--dep_module_unique_name", uniqueModuleName
                )
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        depModuleSubprocess.environment().putAll(RUNFILES_ENV_VARS);
        depModuleSubprocess.start();
      } catch (IOException e) {
        throw new RuntimeException(
            "Internal Compiler Error! Unable to start dep module monomorphization subprocess for module: " +
            uniqueModuleName, e);
      }
    }
    return registry.get(uniqueModuleName).getReadyClient();
  }

  public static void shutdownDepModuleMonomorphization() {
    if (!Objects.isNull(MonomorphizationCoordinator.coordinatorServer)) {
      terminateAllDepModuleMonomorphizationSubprocesses();
      MonomorphizationCoordinator.coordinatorServer.shutdown();
    }
  }

  // Each dep module monomorphization subprocess has been configured to block indefinitely on a call to
  private static void terminateAllDepModuleMonomorphizationSubprocesses() {
    SubprocessRegistration.getRegisteredMonomorphizationSubprocessesByUniqueModuleName().values().forEach(
        depModuleMonomorphizationSubprocessState -> depModuleMonomorphizationSubprocessState.getDoneFuture().set("true")
    );
  }
}
