package com.claro.compiler_backends.java_source.monomorphization;

import com.claro.compiler_backends.java_source.monomorphization.ipc_coordinator.SubprocessRegistration;
import com.claro.compiler_backends.java_source.monomorphization.proto.ipc_protos.IPCMessages;
import com.claro.compiler_backends.java_source.monomorphization.proto.ipc_protos.IPCMessages.MonomorphizationRequest;
import com.claro.module_system.module_serialization.proto.claro_types.TypeProtos;
import com.claro.runtime_utilities.ClaroRuntimeUtilities;
import com.claro.runtime_utilities.http.$ClaroHttpServer;
import com.claro.runtime_utilities.http.$HttpUtil;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$ipc$monomorphization_ipc.DepModuleMonomorphizationService;
import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$ipc$monomorphization_ipc.sendMessageToSubprocess_TriggerMonomorphization;
import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$monomorphization_ipc_coordinator.getDepModuleCoordinatorServerForFreePort;
import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$monomorphization_ipc_coordinator.startCoordinatorServerAndAwaitShutdown;

public class TestCoordinator {

  private static int coordinatorPort = -1;
  private static $ClaroHttpServer coordinatorServer = null;
  // This cache enables us to ensure that we don't make IPC calls for monomorphizations that have already been retrieved.
  private static final HashBasedTable<String, MonomorphizationRequest, String>
      monomorphizationsByModuleAndRequestCache = HashBasedTable.create();

  public static void main(String[] args) {
    Scanner sc = new Scanner(System.in);
    while (true) {
      System.out.println("Coordinator: Trigger monomorphization? (Enter dep module name).... Or shutdown? (Hit enter)");
      String module = sc.nextLine();
      if (module.isEmpty()) {
        break;
      }
      String procedureName = "";
      while (procedureName.isEmpty()) {
        System.out.println("Coordinator: What's the name of the procedure to monomorphize?: ");
        procedureName = sc.nextLine();
      }
      MonomorphizationRequest monomorphizationRequest = getTestMonomorphizationRequest(procedureName);
      String monomorphizationResponse = getDepModuleMonomorphization(module, monomorphizationRequest);
      System.out.println("Coordinator: Here's the monomorphization response: " + monomorphizationResponse);
    }

    // Just do shutdown immediately to observe the subprocess killing itself as well.
    System.out.println("\nTESTING!!! COORDINATOR SHUTTING DOWN MONOMORPHIZATION SUBPROCESSES.");
    if (!Objects.isNull(coordinatorServer)) {
      terminateAllDepModuleMonomorphizationSubprocesses();
      coordinatorServer.shutdown();
    }

    // TODO(steving) The very last thing that this prototype needs to address is the possibility that the
    //  monomorphization requests actually trigger a transitive dep module monomorphization to be needed.

    // Now, for demonstration purposes, "codegen" some representative pseudo-Java.
    if (!monomorphizationsByModuleAndRequestCache.isEmpty()) {
      StringBuilder monomorphizationsCodegen = new StringBuilder();
      for (String module : monomorphizationsByModuleAndRequestCache.rowMap().keySet()) {
        monomorphizationsCodegen.append("private class ").append(module).append(" {\n");
        for (Map.Entry<MonomorphizationRequest, String> entry : monomorphizationsByModuleAndRequestCache.row(module)
            .entrySet()) {
          monomorphizationsCodegen.append("\t/* Monomorphization for:\n\t")
              .append(entry.getKey().toString().replaceAll("\n", ""))
              .append("\n\t*/\n");
          monomorphizationsCodegen.append("\t").append(entry.getValue()).append("\n");
        }
        monomorphizationsCodegen.append("}\n");
      }
      System.out.println("\n\nCOORDINATOR: HERE'S THE MONOMORPHIZATION CODEGEN:\n\n");
      System.out.println(monomorphizationsCodegen);
      System.out.println("\n\n");
    }

    System.out.println("TESTING!!! COORDINATOR EXITED.");
  }

  // TODO(steving) This would need to be updated to take a list of concrete type params.
  private static MonomorphizationRequest getTestMonomorphizationRequest(String procedureName) {
    return MonomorphizationRequest.newBuilder()
        .setProcedureName(procedureName)
        .addAllConcreteTypeParams(
            ImmutableList.of(
                TypeProtos.TypeProto.newBuilder()
                    .setAtom(
                        TypeProtos.AtomType.newBuilder()
                            .setName("TEST_TYPE_A")
                            .setDefiningModuleDisambiguator(""))
                    .build(),
                TypeProtos.TypeProto.newBuilder()
                    .setAtom(
                        TypeProtos.AtomType.newBuilder()
                            .setName("TEST_TYPE_B")
                            .setDefiningModuleDisambiguator(""))
                    .build()
            ))
        .build();
  }

  public static HashMap<String, SubprocessRegistration.DepModuleMonomorphizationSubprocessState>
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
    new Thread(() -> {
      startCoordinatorServerAndAwaitShutdown.apply(coordinatorServer, "COORDINATOR STARTED!");

      // Cleanup once something triggers shutdown. W/o this the process would hang forever as these threads are
      // going to live forever.
      System.out.println("COORDINATOR SERVER SHUTTING DOWN.");
      ClaroRuntimeUtilities.$shutdownAndAwaitTermination(ClaroRuntimeUtilities.DEFAULT_EXECUTOR_SERVICE);
      $HttpUtil.shutdownOkHttpClient();
      System.out.println("COORDINATOR DONE.");
    }).start();
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
  private static String /*TODO(steving) Should just return void, there's no point to this returning a value early*/
  getDepModuleMonomorphization(String module, MonomorphizationRequest depModuleMonomorphizationReq) {
    // See if the IPC req can be skipped in the case that this monomorphization has already been retrieved previously.
    {
      String cachedRes;
      if ((cachedRes = monomorphizationsByModuleAndRequestCache.get(module, depModuleMonomorphizationReq)) != null) {
        System.out.println("Coordinator: This procedure has already been monomorphized. Hit the cache.");
        return cachedRes;
      }
    }
    try {
      // Make a blocking IPC call to the dep module monomorphization subprocess.
      IPCMessages.MonomorphizationResponse monomorphizationRes =
          IPCMessages.MonomorphizationResponse.parseFrom(
              BaseEncoding.base64().decode(
                  Futures.transform(
                      getDepModuleMonomorphizationSubprocessClient(module),
                      depModuleMonomorphizationService -> sendMessageToSubprocess_TriggerMonomorphization.apply(
                          depModuleMonomorphizationService,
                          BaseEncoding.base64().encode(depModuleMonomorphizationReq.toByteArray())
                      ).get(),
                      MoreExecutors.directExecutor()
                  ).get()));
      String directlyRequestedMonomorphizationCodegen = null;
      // Store all local monomorphizations returned by the dep module subprocess, they'll need to be included in the
      // module's codegen.
      for (IPCMessages.MonomorphizationResponse.Monomorphization monomorphization : monomorphizationRes.getLocalModuleMonomorphizationsList()) {
        if (monomorphization.getMonomorphizationRequest().equals(depModuleMonomorphizationReq)) {
          directlyRequestedMonomorphizationCodegen = monomorphization.getMonomorphizationCodegen();
        }
        // Cache the result so that we can avoid duplicate calls in the future.
        monomorphizationsByModuleAndRequestCache.put(
            module, monomorphization.getMonomorphizationRequest(), monomorphization.getMonomorphizationCodegen());
      }
      // Handle any transitive dep module monomorphizations that were requested by the dep module subprocess.
      for (Map.Entry<String, MonomorphizationRequest> transitiveDepModuleMonomorphizationReq :
          monomorphizationRes.getTransitiveDepModuleMonomorphizationRequestsMap().entrySet()) {
        // I want to have the current module's compilation process move in as much of a straight line as possible, so
        // for now, I'm going to immediately request the monomorphization from this transitive dep module.
        getDepModuleMonomorphization(
            transitiveDepModuleMonomorphizationReq.getKey(), transitiveDepModuleMonomorphizationReq.getValue());
      }

      return directlyRequestedMonomorphizationCodegen;
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException("Internal Compiler Error! Failed to get dep module monomorphization from subprocess.", e);
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException("Internal Compiler Error! Failed to parse MonomorphizationResponse proto.", e);
    }
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
        System.out.println("TESTING! STARTING SUBPROCESS!");
        Process depModuleSubprocess =
            new ProcessBuilder()
//                .command("java", "-jar", "bazel-bin/src/java/com/claro/compiler_backends/java_source/monomorphization/dep_module_monomorphization_deploy.jar", "--coordinator_port", String.valueOf(coordinatorPort), "--dep_module_file_path", "foo/bar",
                .command("java", "-jar", "src/java/com/claro/compiler_backends/java_source/monomorphization/dep_module_monomorphization_deploy.jar", "--coordinator_port", String.valueOf(coordinatorPort), "--dep_module_file_path", "foo/bar",
                         // TODO(steving) TESTING! DELETE THIS, The uniqe name should be looked up in the .claro_module.
                         "--dep_module_unique_name", uniqueModuleName
                )
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
      } catch (IOException e) {
        throw new RuntimeException(
            "Internal Compiler Error! Unable to start dep module monomorphization subprocess for module: " +
            uniqueModuleName, e);
      }
    }
    return registry.get(uniqueModuleName).getReadyClient();
  }

  // Each dep module monomorphization subprocess has been configured to block indefinitely on a call to
  private static void terminateAllDepModuleMonomorphizationSubprocesses() {
    SubprocessRegistration.getRegisteredMonomorphizationSubprocessesByUniqueModuleName().values().forEach(
        depModuleMonomorphizationSubprocessState -> depModuleMonomorphizationSubprocessState.getDoneFuture().set("true")
    );
  }
}
