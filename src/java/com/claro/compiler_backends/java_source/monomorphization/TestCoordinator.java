package com.claro.compiler_backends.java_source.monomorphization;

import com.claro.runtime_utilities.ClaroRuntimeUtilities;
import com.claro.runtime_utilities.http.$ClaroHttpServer;
import com.claro.runtime_utilities.http.$HttpUtil;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$monomorphization_ipc.DepModuleMonomorphizationService;
import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$monomorphization_ipc.sendMessageToSubprocess_TriggerMonomorphization;
import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$monomorphization_ipc_coordinator.getDepModuleCoordinatorServerForFreePort;
import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$monomorphization_ipc_coordinator.startCoordinatorServerAndAwaitShutdown;

public class TestCoordinator {

  private static int coordinatorPort = -1;
  private static $ClaroHttpServer coordinatorServer = null;

  public static void main(String[] args) {
    Scanner sc = new Scanner(System.in);
    while (true) {
      System.out.println("Coordinator: Trigger monomorphization? (Enter dep module name).... Or shutdown? (Hit enter)");
      String module = sc.nextLine();
      if (module.isEmpty()) {
        break;
      }
      System.out.println(
          "Coordinator: Here's the monomorphization response: " +
          getDepModuleMonomorphization(module, "TEST_MONOMORPHIZATION_REQ")
      );
    }

    // Just do shutdown immediately to observe the subprocess killing itself as well.
    System.out.println("\nTESTING!!! COORDINATOR SHUTTING DOWN.");
    if (!Objects.isNull(coordinatorServer)) {
      terminateAllDepModuleMonomorphizationSubprocesses();
      coordinatorServer.shutdown();
    }
    System.out.println("TESTING!!! COORDINATOR EXITED.");
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

  // TODO(steving) Update this function to take a real proto request and return GeneratedJavaSource.
  private static String getDepModuleMonomorphization(String module, String depModuleMonomorphizationReq) {
    try {
      return Futures.transform(
          getDepModuleMonomorphizationSubprocessClient(module),
          depModuleMonomorphizationService -> sendMessageToSubprocess_TriggerMonomorphization.apply(
              depModuleMonomorphizationService, depModuleMonomorphizationReq).get(),
          MoreExecutors.directExecutor()
      ).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException("Internal Compiler Error! Failed to get dep module monomorphization from subprocess.", e);
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
                .command("java", "-jar", "bazel-bin/src/java/com/claro/compiler_backends/java_source/monomorphization/dep_module_monomorphization_deploy.jar", "--coordinator_port", String.valueOf(coordinatorPort), "--dep_module_file_path", "foo/bar",
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
