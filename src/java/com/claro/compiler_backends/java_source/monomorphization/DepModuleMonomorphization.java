package com.claro.compiler_backends.java_source.monomorphization;

import com.claro.ClaroCompilerMain;
import com.claro.compiler_backends.java_source.JavaSourceCompilerBackend;
import com.claro.compiler_backends.java_source.monomorphization.ipc.MonomorphizationRequestProcessing;
import com.claro.intermediate_representation.ProgramNode;
import com.claro.intermediate_representation.statements.GenericFunctionDefinitionStmt;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.statements.StmtListNode;
import com.claro.intermediate_representation.statements.user_defined_type_def_stmts.InitializersBlockStmt;
import com.claro.intermediate_representation.statements.user_defined_type_def_stmts.UnwrappersBlockStmt;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.claro.runtime_utilities.ClaroRuntimeUtilities;
import com.claro.runtime_utilities.http.$ClaroHttpServer;
import com.claro.runtime_utilities.http.$HttpUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.common.options.OptionsParser;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$ipc$monomorphization_ipc.getDepModuleMonomorphizationServerForFreePort;
import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$ipc$monomorphization_ipc.startMonomorphizationServerAndAwaitShutdown;
import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$ipc_coordinator$monomorphization_ipc_coordinator.*;

public class DepModuleMonomorphization {

  private final $ClaroHttpServer server;
  private final DepModuleCoordinatorService coordinatorClient;
  private final ImmutableList<String> recompilationArgs;

  public static void main(String[] args) throws Exception {
    DepModuleMonomorphizationCLIOptions options = parseCLIOptions(args);

    // First thing first, parse the specified .claro_module to discover the unique module name as well as the cli args
    // to restart compilation with.
    SerializedClaroModule parsedModule =
        SerializedClaroModule.parseDelimitedFrom(
            Files.newInputStream(FileSystems.getDefault().getPath(options.depModuleFilePath), StandardOpenOption.READ));

    String uniqueModuleName = parsedModule.getModuleDescriptor().getUniqueModuleName();

    // Here I need to immediately trigger the server to startup so that I can be ready to receive monomorphization reqs
    // from the coordinator compilation unit.
    DepModuleMonomorphization monomorphizer;
    {
      DepModuleCoordinatorService coordinatorClient = getDepModuleCoordinatorClient.apply(options.coordinatorPort);
      monomorphizer =
          new DepModuleMonomorphization(
              startMonomorphizationServerInNewThread(uniqueModuleName, coordinatorClient),
              coordinatorClient,
              // TODO(steving) Unfortunately, for now, the "bootstrapping" version of the SerializedClaroModule.java file
              //     doesn't have access to the command_line_args field as it's a version behind. After I push the first
              //     version of Claro that actually supports dep module monomorphization, I should refactor to call directly.
              ImmutableList.copyOf(parsedModule.getCommandLineArgsList())
          );
    }

    // I'm actually going to want to run *all* of the initial setup and type checking before actually accepting any
    // monomorphization reqs. So doing that all here before reporting "ready" back to the coordinator.
    monomorphizer.runModuleCompilationPreworkBeforeMonomorphizationWorkPossible();

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
  }

  // I'll make an instance of this literally just so I can have final variables.
  private DepModuleMonomorphization(
      $ClaroHttpServer server, DepModuleCoordinatorService coordinatorClient, ImmutableList<String> recompilationArgs) {
    this.server = server;
    this.coordinatorClient = coordinatorClient;
    // TODO(steving) TESTING!!! FOR NOW I'LL NEED TO FILTER OUT ARGS THAT THE "BOOTSTRAPPING" COMPILER WON'T BE READY
    //   FOR BECAUSE THAT WOULD CAUSE THE PROCESS TO EXIT.
    ImmutableList.Builder<String> recompilationArgsBuilder = ImmutableList.<String>builder().add("--java_source");
    for (String arg : recompilationArgs) {
      // Once you find a new arg, bail.
      if (arg.equals("--dep_graph_claro_module_by_unique_name")) {
        break;
      }
      recompilationArgsBuilder.add(arg);
    }
    this.recompilationArgs = recompilationArgsBuilder.build();
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
    $ClaroHttpServer.silent = true;

    // I'll need to actually start the server before reporting its port so that by the time there's a call it's
    // actually running.
    new Thread(() -> {
      // This line runs forever - until the server receives the shutdown command.
      startMonomorphizationServerAndAwaitShutdown.apply(monomorphizationServer);

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

  // NOTE: It's important to keep in mind the subtle detail that the compiler being invoked here is the *BOOTSTRAPPING*
  //       compiler, not the latest (local) version of the compiler. This is important in that any local modifications
  //       made to the source of the compiler will not reflect here until included in a new release.
  private void runModuleCompilationPreworkBeforeMonomorphizationWorkPossible() throws InterruptedException {
    try {
      // Make sure to signal to the compiler that we are doing dep module monomorphization and so must follow simplified
      // compilation process.
      JavaSourceCompilerBackend.DEP_MODULE_MONOMORPHIZATION_ENABLED = true;
      InternalStaticStateUtil.DEP_MODULE_MONOMORPHIZATION_ENABLED = true;
      ClaroCompilerMain.main(this.recompilationArgs.toArray(new String[this.recompilationArgs.size()]));

      // Setup MonomorphizationRequestProcessing to be able selectively trigger type validation on generic procedures
      // as they're requested by the monomorphization coordinator.
      ImmutableMap.Builder<String, GenericFunctionDefinitionStmt> genericFunctionDefinitionStmtBuilder =
          ImmutableMap.builder();
      for (ProgramNode currNonMainProgramNode : ProgramNode.nonMainFiles) {
        StmtListNode currStmtListNode = currNonMainProgramNode.stmtListNode;
        while (currStmtListNode != null) {
          Stmt currStmt = (Stmt) currStmtListNode.getChildren().get(0);
          if (currStmt instanceof GenericFunctionDefinitionStmt) {
            genericFunctionDefinitionStmtBuilder.put(
                ((GenericFunctionDefinitionStmt) currStmt).functionName, (GenericFunctionDefinitionStmt) currStmt);
          } else if (currStmt instanceof InitializersBlockStmt) {
            ((InitializersBlockStmt) currStmt).initializerProcedureDefs.stream()
                .filter(proc -> proc instanceof GenericFunctionDefinitionStmt)
                .forEach(
                    proc -> {
                      GenericFunctionDefinitionStmt genProc = (GenericFunctionDefinitionStmt) proc;
                      genericFunctionDefinitionStmtBuilder.put(genProc.functionName, genProc);
                    });
          } else if (currStmt instanceof UnwrappersBlockStmt) {
            ((UnwrappersBlockStmt) currStmt).unwrapperProcedureDefs.stream()
                .filter(proc -> proc instanceof GenericFunctionDefinitionStmt)
                .forEach(
                    proc -> {
                      GenericFunctionDefinitionStmt genProc = (GenericFunctionDefinitionStmt) proc;
                      genericFunctionDefinitionStmtBuilder.put(genProc.functionName, genProc);
                    });
          }
          currStmtListNode = currStmtListNode.tail;
        }
      }
      MonomorphizationRequestProcessing.genericFunctionDefinitionStmtsByName =
          genericFunctionDefinitionStmtBuilder.build();
    } catch (Exception e) {
      System.err.println("TESTING!! Internal Compiler Error! FAILED RECOMPILATION SOMEHOW:");
      e.printStackTrace();
    }
  }
}
