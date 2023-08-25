package com.claro.compiler_backends.java_source.monomorphization;

import com.claro.runtime_utilities.ClaroRuntimeUtilities;
import com.claro.runtime_utilities.http.$ClaroHttpServer;
import com.claro.runtime_utilities.http.$HttpUtil;
import com.google.devtools.common.options.OptionsParser;

import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$monomorphization_ipc.getDepModuleMonomorphizationServerForFreePort;
import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$monomorphization_ipc.startMonomorphizationServerAndAwaitShutdown;
import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$monomorphization_ipc_coordinator.*;

public class DepModuleMonomorphization {

  private final $ClaroHttpServer server;
  private final DepModuleCoordinatorService coordinatorClient;

  public static void main(String[] args) throws Exception {
    DepModuleMonomorphizationCLIOptions options = parseCLIOptions(args);

    System.out.println("TESTING!!! MADE IT INTO THE CHILD PROCESS: " + options);

    // TODO(steving) UPDATE TO READ AN ACTUAL .claro_module FILE TO GET THE UNIQUE MODULE NAME.
    String uniqueModuleName = options.depModuleUniqueName;

    // Here I need to immediately trigger the server to startup so that I can be ready to receive monomorphization reqs
    // from the coordinator compilation unit.
    DepModuleMonomorphization monomorphizer;
    {
      DepModuleCoordinatorService coordinatorClient = getDepModuleCoordinatorClient.apply(options.coordinatorPort);
      monomorphizer =
          new DepModuleMonomorphization(
              startMonomorphizationServerInNewThread(uniqueModuleName, coordinatorClient),
              coordinatorClient
          );
    }

    // TODO(steving) In practice, I'm actually going to want to run *all* of the initial setup and type checking before
    //   actually accepting any monomorphization reqs. So do that all here before reporting "ready" back to the coordinator.
    runModuleCompilationPreworkBeforeMonomorphizationWorkPossible();

    // Now report back to the coordinator that we're ready to handle monomorphization requests. We'll intentionally
    // block forever on this response because this signals this process to stay alive so long as this connection is
    // active. If for any reason this get() call returns, that indicates that this subprocess should terminate in order
    // to prevent being orphaned. This approach is a clever (read: hacky) workaround that is intended to handle both
    // healthy shutdown, and fault-tolerant shutdown in the case that the coordinator process is killed without being
    // given the chance to do cleanup.
    String coordinatorReadyResponse = sendMessageToCoordinator_markDepModuleSubprocessReady
        .apply(monomorphizer.coordinatorClient, uniqueModuleName).get();

    if (!Boolean.parseBoolean(coordinatorReadyResponse)) {
      System.err.println(
          "Dep Module Monomorphization Subprocess (" + uniqueModuleName + "):\n\tObserved from unhealthy shutdown" +
          " of originating compilation unit coordinator process.\n\tCaused by: " + coordinatorReadyResponse + "\n" +
          "Subprocess exiting...");
    }

    System.out.println("TESTING! SHUTTING DOWN DEP MODULE SUBPROCESS.");
    // Our connection to the coordinator has terminated, time to shutdown.
    monomorphizer.server.shutdown();
    System.out.println("TESTING! DEP MODULE SUBPROCESS EXITING.");
  }

  // I'll make an instance of this literally just so I can have final variables.
  private DepModuleMonomorphization($ClaroHttpServer server, DepModuleCoordinatorService coordinatorClient) {
    this.server = server;
    this.coordinatorClient = coordinatorClient;
  }

  private static DepModuleMonomorphizationCLIOptions parseCLIOptions(String... args) {
    OptionsParser parser = OptionsParser.newOptionsParser(DepModuleMonomorphizationCLIOptions.class);
    parser.parseAndExitUponError(args);
    return parser.getOptions(DepModuleMonomorphizationCLIOptions.class);
  }

  private static $ClaroHttpServer startMonomorphizationServerInNewThread(
      String uniqueModuleName, DepModuleCoordinatorService coordinatorClient) {
    $ClaroHttpServer monomorphizationServer = getDepModuleMonomorphizationServerForFreePort.apply();
    int monomorphizationServerPort = monomorphizationServer.server.getListenAddresses().get(0).getPort();
    System.out.println("TESTING!!! DEP MODULE SERVER LISTENING ON PORT: " + monomorphizationServerPort);

    // I'll need to actually start the server before reporting its port so that by the time there's a call it's
    // actually running.
    new Thread(() -> {
      // This line runs forever - until the server receives the shutdown command.
      startMonomorphizationServerAndAwaitShutdown.apply(monomorphizationServer, "DEP MODULE STARTED!");

      // Cleanup once something triggers shutdown. W/o this the process would hang forever as these threads are
      // going to live forever.
      ClaroRuntimeUtilities.$shutdownAndAwaitTermination(ClaroRuntimeUtilities.DEFAULT_EXECUTOR_SERVICE);
      $HttpUtil.shutdownOkHttpClient();
    }).start();

    // Now report the listening port back to the coordinator right away so that we can ensure that the coordinator can
    // actually shutdown this server when it's done.
    String registerPortResponse;
    boolean coordinatorPortRegistrationSuccess =
        Boolean.parseBoolean(
            registerPortResponse = sendMessageToCoordinator_registerDepModulePortWithCoordinator.apply(
                coordinatorClient, uniqueModuleName, String.valueOf(monomorphizationServerPort)).get());

    // If, by some unfortunate timing, the coordinator was unable to respond to this message b/c it was already shutdown
    // or something then we need to abort this process immediately to avoid orphaning this process.
    if (!coordinatorPortRegistrationSuccess) {
      System.err.println(
          "Internal Compiler Error! Dep Module Monomorphization Subprocess (" + uniqueModuleName + "):\n\tUnable to " +
          "register monomorphization subprocess with originating compilation unit coordinator process.");
      System.err.println("\tCaused by: " + registerPortResponse);
      System.err.println("Subprocess exiting.");
      System.exit(1);
    }

    // Return a ref to the server just so that it can be shutdown manually later.
    return monomorphizationServer;
  }

  private static void runModuleCompilationPreworkBeforeMonomorphizationWorkPossible() throws InterruptedException {
    System.out.println("TESTING!!! PREWORK...sleeping 1 second...");
    Thread.sleep(1000);
  }
}
