package com.claro.compiler_backends.java_source.monomorphization.ipc_coordinator;

import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.builtins_impls.futures.ClaroFuture;
import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.SettableFuture;

import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$ipc$main_compilation_unit_monomorphization_ipc.DepModuleMonomorphizationService;
import static claro.lang.src$java$com$claro$compiler_backends$java_source$monomorphization$ipc$main_compilation_unit_monomorphization_ipc.getDepModuleMonomorphizationClient;

public class SubprocessRegistration {
  public static SettableFuture<HashMap<String, DepModuleMonomorphizationSubprocessState>>
      registeredMonomorphizationSubprocessesByUniqueModuleName = SettableFuture.create();

  public static void registerPort(String uniqueModuleName, String port) {
    HashMap<String, DepModuleMonomorphizationSubprocessState> registry =
        getRegisteredMonomorphizationSubprocessesByUniqueModuleName();

    if (!registry.containsKey(uniqueModuleName)) {
      throw new RuntimeException("Internal Compiler Error! Coordinator attempting to mark a dep module " +
                                 "monomorphization subprocess port for an unregistered dep module.");
    } else if (registry.get(uniqueModuleName).getPortFuture().isDone()) {
      throw new RuntimeException("Internal Compiler Error! Coordinator attempting to mark a dep module " +
                                 "monomorphization subprocess port more than once.");
    }
    registry.get(uniqueModuleName).getPortFuture().set(Integer.parseInt(port));
  }

  public static ClaroFuture<String> markDepModuleSubprocessReady(String uniqueModuleName) {
    DepModuleMonomorphizationSubprocessState subprocessState =
        getRegisteredMonomorphizationSubprocessesByUniqueModuleName().get(uniqueModuleName);
    subprocessState.markReady();

    return new ClaroFuture<>(Types.FutureType.wrapping(Types.STRING), subprocessState.getDoneFuture());
  }

  public static HashMap<String, DepModuleMonomorphizationSubprocessState> getRegisteredMonomorphizationSubprocessesByUniqueModuleName() {
    try {
      return SubprocessRegistration.registeredMonomorphizationSubprocessesByUniqueModuleName.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException("Internal Compiler Error! Attempting to register subprocess port before starting the Coordinator server.", e);
    }
  }

  @AutoValue
  public abstract static class DepModuleMonomorphizationSubprocessState {
    abstract SettableFuture<Integer> getPortFuture();

    public abstract SettableFuture<DepModuleMonomorphizationService> getReadyClient();

    // This future will not be marked complete until the Coordinator decides that it's done with all monomorphizations
    // from this dep module. This should be used by the DepModuleCoordinatorService::markDepModuleSubprocessReady
    // endpoint to be able to delay the response so that the connection remains open and the subprocess uses that as
    // signal to keepalive.
    public abstract SettableFuture<String> getDoneFuture();

    public static DepModuleMonomorphizationSubprocessState create() {
      return new AutoValue_SubprocessRegistration_DepModuleMonomorphizationSubprocessState(
          SettableFuture.create(), SettableFuture.create(), SettableFuture.create());
    }

    public void markReady() {
      if (this.getReadyClient().isDone()) {
        throw new RuntimeException("Internal Compiler Error! Coordinator attempting to mark a dep module " +
                                   "monomorphization subprocess ready more than once.");
      }
      try {
        this.getReadyClient().set(getDepModuleMonomorphizationClient.apply(this.getPortFuture().get()));
      } catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException(
            "Internal Compiler Error! Attempting to mark a dep module monomorphization subprocess ready before its " +
            "port has been registered.", e);
      }
    }

    public void markDone() {
      if (this.getDoneFuture().isDone()) {
        throw new RuntimeException("Internal Compiler Error! Coordinator attempting to mark a dep module " +
                                   "monomorphization subprocess done more than once.");
      }
      this.getDoneFuture().set("true");
    }
  }
}
