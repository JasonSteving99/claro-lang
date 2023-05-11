package com.claro.intermediate_representation;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.procedures.functions.StructuralConcreteGenericTypeValidationUtil;
import com.claro.intermediate_representation.statements.*;
import com.claro.intermediate_representation.statements.contracts.ContractDefinitionStmt;
import com.claro.intermediate_representation.statements.contracts.ContractImplementationStmt;
import com.claro.intermediate_representation.statements.user_defined_type_def_stmts.InitializersBlockStmt;
import com.claro.intermediate_representation.statements.user_defined_type_def_stmts.NewTypeDefStmt;
import com.claro.intermediate_representation.statements.user_defined_type_def_stmts.UnwrappersBlockStmt;
import com.claro.intermediate_representation.statements.user_defined_type_def_stmts.UserDefinedTypeDefinitionStmt;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import java.util.Stack;
import java.util.function.Function;

public class ProgramNode {
  private final String packageString, generatedClassName;
  private StmtListNode stmtListNode;
  public static final Stack<Runnable> miscErrorsFound = new Stack<>();

  private Function<ScopedHeap, ImmutableList<Stmt>> setupStdLibFn = s -> ImmutableList.of();
      // By default, don't support any StdLib.

  // TODO(steving) package and generatedClassName should probably be injected some cleaner way since this is a Target::JAVA_SOURCE-only artifact.
  public ProgramNode(
      StmtListNode stmtListNode,
      String packageString,
      String generatedClassName) {
    this.stmtListNode = stmtListNode;
    this.packageString = packageString;
    this.generatedClassName = generatedClassName;

    // TODO(steving) Fix this hot garbage.
    Expr.StructuralConcreteGenericTypeValidationUtil_validateArgExprsAndExtractConcreteGenericTypeParams_ONLY_USE_BC_MY_BAZEL_SETUP_IS_BJORKED_AND_I_DONT_HAVE_TIME_TO_FIX_THE_CIRCULAR_DEPS
        = (expected, actual) -> {
      try {
        return StructuralConcreteGenericTypeValidationUtil
            .validateArgExprsAndExtractConcreteGenericTypeParams(Maps.newHashMap(), expected, actual);
      } catch (ClaroTypeException e) {
        throw new RuntimeException(e);
      }
    };
  }

  public StringBuilder generateTargetOutput(
      Target target,
      // Injecting this here literally just to keep Bazel from needing a circular dep on ProgramNode via Exec method.
      // TODO(steving) Fix this garbage.
      Function<ScopedHeap, ImmutableList<Stmt>> setupStdLibFn) throws IllegalArgumentException {
    ScopedHeap scopedHeap = new ScopedHeap();
    scopedHeap.enterNewScope();
    return generateTargetOutput(target, scopedHeap, setupStdLibFn);
  }

  public StringBuilder generateTargetOutput(
      Target target,
      ScopedHeap scopedHeap,
      Function<ScopedHeap, ImmutableList<Stmt>> setupStdLibFn) throws IllegalArgumentException {
    this.setupStdLibFn = setupStdLibFn;
    StringBuilder generatedOutput;
    switch (target) {
      case JAVA_SOURCE:
        generatedOutput = generateJavaSourceOutput(scopedHeap);
        break;
      case REPL:
        // We can't check for unused identifiers in the REPL because we might just not yet have seen the instruction
        // where a given identifier will be used.
        scopedHeap.disableCheckUnused();
        // We're gonna be a bit overly clever and allow fallthrough to the next case just for kicks.
      case INTERPRETED:
        generatedOutput = new StringBuilder().append(generateInterpretedOutput(scopedHeap));
        break;
      default:
        throw new IllegalArgumentException("Unexpected Target: " + target);
    }
    return generatedOutput;
  }

  // TODO(steving) This method needs to be refactored and have lots of its logic lifted up out into the callers which
  // TODO(steving) are the actual CompilerBackend's. Most of what's going on here is legit not an AST node's responsibility.
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Setup the StdLib in the current Scope and append any setup Stmts to prefix the given program.
    setupStdLib(scopedHeap);

    // TODO(steving) These Type + Procedure Discovery phases do things in O(2n) time, we really should structure
    // TODO(steving) the response from the parser better so that it's not just a denormalized list of stmts,
    // TODO(steving) instead it should give a structured list of type defs seperate from procedure defs etc.
    // TYPE DISCOVERY PHASE:
    performTypeDiscoveryPhase(stmtListNode, scopedHeap);

    // PROCEDURE DISCOVERY PHASE:
    performProcedureDiscoveryPhase(stmtListNode, scopedHeap);

    // CONTRACT DISCOVERY PHASE:
    performContractDiscoveryPhase(stmtListNode, scopedHeap);

    // GENERIC PROCEDURE DISCOVERY PHASE:
    performGenericProcedureDiscoveryPhase(stmtListNode, scopedHeap);

    // Modules only need to know about procedure type signatures, nothing else, so save procedure type
    // validation for after the full module discovery and validation phases since procedure type validation
    // phase will depend on knowledge of transitive module bindings to validate using-blocks nested in
    // top-level procedures.

    // MODULE DISCOVERY PHASE:
    performModuleDiscoveryPhase(stmtListNode, scopedHeap);

    // MODULE TYPE VALIDATION PHASE:
    performModuleTypeValidationPhase(stmtListNode, scopedHeap);

    // PROCEDURE TYPE VALIDATION PHASE:
    performProcedureTypeValidationPhase(stmtListNode, scopedHeap);

    // CONTRACT TYPE VALIDATION PHASE:
    performContractTypeValidationPhase(stmtListNode, scopedHeap);

    // GENERIC PROCEDURE TYPE VALIDATION PHASE:
    performGenericProcedureTypeValidationPhase(stmtListNode, scopedHeap);

    // Now, force the ScopedHeap into a new Scope, because we want to make it explicit that top-level function
    // definitions live in their own scope and cannot reference variables below. We consider functions defined
    // within contract implementations still as top-level functions.
    scopedHeap.enterNewScope();

    // NON-PROCEDURE/MODULE STATEMENT TYPE VALIDATION PHASE:
    // Validate all types in the entire remaining AST before execution.
    try {
      // TODO(steving) Currently, GenericProcedureDefinitionStmts are getting type checked a second time here for no reason.
      stmtListNode.assertExpectedExprTypes(scopedHeap);
    } catch (ClaroTypeException e) {
      // Java get the ... out of my way and just let me not pollute the interface with a throws modifier.
      // Also let's be fair that I'm just too lazy to make a new RuntimeException version of the ClaroTypeException for
      // use in the execution stage.
      throw new RuntimeException(e);
    }

    // UNUSED CHECKING PHASE:
    // Manually exit the last observed scope which is the global scope, since nothing else will trigger its exit.
    // BUT, because we need the scope to not be thrown away in the REPL case (since in that case we aren't actually
    // exiting the scope, we're just temporarily bouncing out, with the ScopedHeap as the source of continuity between
    // REPL stmts...) we won't do this if it's the repl case. This only loses us "unused" checking, which is disabled in
    // the REPL anyways.
    if (scopedHeap.checkUnused) {
      // Finalize the type-checking phase. In this special case we explicitly want to maintain all of the top level
      // user-defined type data that we evaluated in the preceding phases, so instead of depending on exiting this last
      // scope to trigger checking unused, we'll just manually check unused, but keep the scope so that we keep the type
      // definitions.
      try {
        scopedHeap.checkAllIdentifiersInCurrScopeUsed();
      } catch (Exception e) {
        miscErrorsFound.push(() -> System.err.println(e.getMessage()));
      }
    }

    // CODE GEN PHASE:
    // Refuse to do code-gen phase if there were any type validation errors.
    StringBuilder res = null; // I hate null but am also too lazy right now to refactor to Optional<StringBuilder>
    if (Expr.typeErrorsFound.isEmpty() && miscErrorsFound.isEmpty()) {
      // Now that we've validated that all types are valid, go to town in a fresh scope!
      Node.GeneratedJavaSource programJavaSource =
          stmtListNode.generateJavaSourceOutput(scopedHeap, this.generatedClassName);
      res = genJavaSource(programJavaSource);
    }

    // Just for completeness sake, we'll want to exit this global scope as well just in case there are important checks
    // that get run at that time at the last moment before we give the all good signal.
    try {
      scopedHeap.disableCheckUnused(); // We already checked unused before codegen.
      scopedHeap.exitCurrScope();
    } catch (Exception e) {
      miscErrorsFound.push(() -> System.err.println(e.getMessage()));
    }

    // Wrap the generated source code with the needed Java boilerplate.
    return res;
  }

  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Setup the StdLib in the current Scope and append any setup Stmts to prefix the given program.
    setupStdLib(scopedHeap);

    // TYPE DISCOVERY PHASE:
    performTypeDiscoveryPhase(stmtListNode, scopedHeap);

    // GENERIC PROCEDURE DISCOVERY PHASE:
    performGenericProcedureDiscoveryPhase(stmtListNode, scopedHeap);

    // PROCEDURE DISCOVERY PHASE:
    performProcedureDiscoveryPhase(stmtListNode, scopedHeap);

    // CONTRACT DISCOVERY PHASE:
    performContractDiscoveryPhase(stmtListNode, scopedHeap);

    // Modules only need to know about procedure type signatures, nothing else, so save procedure type
    // validation for after the full module discovery and validation phases since procedure type validation
    // phase will depend on knowledge of transitive module bindings to validate using-blocks nested in
    // top-level procedures.

    // MODULE DISCOVERY PHASE:
    performModuleDiscoveryPhase(stmtListNode, scopedHeap);

    // MODULE TYPE VALIDATION PHASE:
    performModuleTypeValidationPhase(stmtListNode, scopedHeap);

    // CONTRACT TYPE VALIDATION PHASE:
    performContractTypeValidationPhase(stmtListNode, scopedHeap);

    // GENERIC PROCEDURE TYPE VALIDATION PHASE:
    performGenericProcedureTypeValidationPhase(stmtListNode, scopedHeap);

    // PROCEDURE TYPE VALIDATION PHASE:
    performProcedureTypeValidationPhase(stmtListNode, scopedHeap);

    // Now, force the ScopedHeap into a new Scope, because we want to make it explicit that top-level function
    // definitions live in their own scope and cannot reference variables below. We consider functions defined
    // within contract implementations still as top-level functions.
    scopedHeap.enterNewScope();

    // Validate all types in the entire remaining AST before execution.
    try {
      // TYPE VALIDATION PHASE:
      // TODO(steving) Currently, GenericProcedureDefinitionStmts are getting type checked a second time here for no reason.
      stmtListNode.assertExpectedExprTypes(scopedHeap);
    } catch (ClaroTypeException e) {
      // Java get the ... out of my way and just let me not pollute the interface with a throws modifier.
      // Also let's be fair that I'm just too lazy to make a new RuntimeException version of the ClaroTypeException for
      // use in the execution stage.
      throw new RuntimeException(e);
    }

    // Refuse to perform the execution phase if there were any type validation errors.
    if (Expr.typeErrorsFound.isEmpty() && miscErrorsFound.isEmpty()) {
      // Now that we've validated that all types are valid, go to town!
      stmtListNode.generateInterpretedOutput(scopedHeap);
    }

    // There's no output in the interpreting mode.
    return null;
  }

  private void setupStdLib(ScopedHeap scopedHeap) {
    ImmutableList<Stmt> stdlibSetupPrefixStmts = this.setupStdLibFn.apply(scopedHeap);
    if (!stdlibSetupPrefixStmts.isEmpty()) {
      StmtListNode programStmtListNode = this.stmtListNode;
      this.stmtListNode = new StmtListNode(stdlibSetupPrefixStmts.get(0));
      StmtListNode prevPrefixStmtListNode = this.stmtListNode;
      for (Stmt currPrefixStmt : stdlibSetupPrefixStmts.subList(1, stdlibSetupPrefixStmts.size())) {
        prevPrefixStmtListNode.tail = new StmtListNode(currPrefixStmt);
        prevPrefixStmtListNode = prevPrefixStmtListNode.tail;
      }
      // Reattach the user-given program after the stdlib prefix stmts.
      prevPrefixStmtListNode.tail = programStmtListNode;
    }
  }

  private void performTypeDiscoveryPhase(StmtListNode stmtListNode, ScopedHeap scopedHeap) {
    // Very first things first, because we want to allow references of user-defined types anywhere in the scope
    // regardless of what line the type was defined on, we need to first explicitly and pick all of the user-defined
    // type definition stmts and do a first pass of registering their TypeProviders in the symbol table. These
    // TypeProviders will be resolved recursively in the immediately following type-validation phase.
    StmtListNode currStmtListNode = stmtListNode;
    while (currStmtListNode != null) {
      Stmt currStmt = (Stmt) currStmtListNode.getChildren().get(0);
      if (currStmt instanceof UserDefinedTypeDefinitionStmt) {
        ((UserDefinedTypeDefinitionStmt) currStmt).registerTypeProvider(scopedHeap);
      } else if (currStmt instanceof HttpServiceDefStmt) {
        ((HttpServiceDefStmt) currStmt).registerTypeProvider(scopedHeap);
      }
      currStmtListNode = currStmtListNode.tail;
    }
  }

  private void performContractDiscoveryPhase(StmtListNode stmtListNode, ScopedHeap scopedHeap) {
    // First need to discover all of the Contract Definitions before we can register any of the implementations.
    ImmutableList.Builder<ContractDefinitionStmt> contractDefinitionStmts = ImmutableList.builder();
    StmtListNode currStmtListNode = stmtListNode;
    while (currStmtListNode != null) {
      Stmt currStmt = (Stmt) currStmtListNode.getChildren().get(0);
      if (currStmt instanceof ContractDefinitionStmt) {
        contractDefinitionStmts.add((ContractDefinitionStmt) currStmt);
        try {
          currStmt.assertExpectedExprTypes(scopedHeap);
        } catch (ClaroTypeException e) {
          // Java get the ... out of my way and just let me not pollute the interface with a throws modifier.
          // Also let's be fair that I'm just too lazy to make a new RuntimeException version of the ClaroTypeException for
          // use in the execution stage.
          throw new RuntimeException(e);
        }
      }
      currStmtListNode = currStmtListNode.tail;
    }

    // Now that the Contract Definitions are all known, we can go ahead and register the implementations.
    currStmtListNode = stmtListNode;
    while (currStmtListNode != null) {
      Stmt currStmt = (Stmt) currStmtListNode.getChildren().get(0);
      if (currStmt instanceof ContractImplementationStmt) {
        ((ContractImplementationStmt) currStmt).registerProcedureTypeProviders(scopedHeap);
      }
      currStmtListNode = currStmtListNode.tail;
    }

    // Now we simply need to register the dynamic dispatch handlers for each Contract Procedure.
    for (ContractDefinitionStmt currContract : contractDefinitionStmts.build()) {
      currContract.registerDynamicDispatchHandlers(scopedHeap);
    }
  }

  private void performContractTypeValidationPhase(StmtListNode stmtListNode, ScopedHeap scopedHeap) {
    // Very first things first, because we want to allow references of user-defined types anywhere in the scope
    // regardless of what line the type was defined on, we need to first explicitly and pick all of the user-defined
    // type definition stmts and do a first pass of registering their TypeProviders in the symbol table. These
    // TypeProviders will be resolved recursively in the immediately following type-validation phase.
    StmtListNode currStmtListNode = stmtListNode;
    while (currStmtListNode != null) {
      Stmt currStmt = (Stmt) currStmtListNode.getChildren().get(0);
      if (currStmt instanceof ContractImplementationStmt) {
        try {
          currStmt.assertExpectedExprTypes(scopedHeap);
        } catch (ClaroTypeException e) {
          // Java get the ... out of my way and just let me not pollute the interface with a throws modifier.
          // Also let's be fair that I'm just too lazy to make a new RuntimeException version of the ClaroTypeException for
          // use in the execution stage.
          throw new RuntimeException(e);
        }
      }
      currStmtListNode = currStmtListNode.tail;
    }
  }

  private void performGenericProcedureTypeValidationPhase(StmtListNode stmtListNode, ScopedHeap scopedHeap) {
    // Very first things first, because we want to allow references of user-defined types anywhere in the scope
    // regardless of what line the type was defined on, we need to first explicitly and pick all of the user-defined
    // type definition stmts and do a first pass of registering their TypeProviders in the symbol table. These
    // TypeProviders will be resolved recursively in the immediately following type-validation phase.
    StmtListNode currStmtListNode = stmtListNode;
    while (currStmtListNode != null) {
      Stmt currStmt = (Stmt) currStmtListNode.getChildren().get(0);
      if (currStmt instanceof GenericFunctionDefinitionStmt) {
        try {
          currStmt.assertExpectedExprTypes(scopedHeap);
        } catch (ClaroTypeException e) {
          // Java get the ... out of my way and just let me not pollute the interface with a throws modifier.
          // Also let's be fair that I'm just too lazy to make a new RuntimeException version of the ClaroTypeException for
          // use in the execution stage.
          throw new RuntimeException(e);
        }
      }
      currStmtListNode = currStmtListNode.tail;
    }
    InternalStaticStateUtil.GnericProcedureDefinitionStmt_doneWithGenericProcedureTypeValidationPhase = true;
  }

  private void performGenericProcedureDiscoveryPhase(StmtListNode stmtListNode, ScopedHeap scopedHeap) {
    // Very first things first, because we want to allow references of user-defined types anywhere in the scope
    // regardless of what line the type was defined on, we need to first explicitly and pick all of the user-defined
    // type definition stmts and do a first pass of registering their TypeProviders in the symbol table. These
    // TypeProviders will be resolved recursively in the immediately following type-validation phase.
    StmtListNode currStmtListNode = stmtListNode;
    while (currStmtListNode != null) {
      Stmt currStmt = (Stmt) currStmtListNode.getChildren().get(0);
      if (currStmt instanceof GenericFunctionDefinitionStmt) {
        try {
          ((GenericFunctionDefinitionStmt) currStmt).registerGenericProcedureTypeProvider(scopedHeap);
        } catch (ClaroTypeException e) {
          // Java get the ... out of my way and just let me not pollute the interface with a throws modifier.
          // Also let's be fair that I'm just too lazy to make a new RuntimeException version of the ClaroTypeException for
          // use in the execution stage.
          throw new RuntimeException(e);
        }
      }
      currStmtListNode = currStmtListNode.tail;
    }
  }

  private void performProcedureDiscoveryPhase(StmtListNode stmtListNode, ScopedHeap scopedHeap) {
    // Very first things first, because we want to allow references of user-defined types anywhere in the scope
    // regardless of what line the type was defined on, we need to first explicitly and pick all of the user-defined
    // type definition stmts and do a first pass of registering their TypeProviders in the symbol table. These
    // TypeProviders will be resolved recursively in the immediately following type-validation phase.
    StmtListNode currStmtListNode = stmtListNode;
    while (currStmtListNode != null) {
      Stmt currStmt = (Stmt) currStmtListNode.getChildren().get(0);
      if (currStmt instanceof ProcedureDefinitionStmt) {
        ((ProcedureDefinitionStmt) currStmt).registerProcedureTypeProvider(scopedHeap);
      } else if (currStmt instanceof NewTypeDefStmt) {
        ((NewTypeDefStmt) currStmt).registerConstructorTypeProvider(scopedHeap);
      } else if (currStmt instanceof InitializersBlockStmt) {
        ((InitializersBlockStmt) currStmt).registerProcedureTypeProviders(scopedHeap);
      } else if (currStmt instanceof UnwrappersBlockStmt) {
        ((UnwrappersBlockStmt) currStmt).registerProcedureTypeProviders(scopedHeap);
      } else if (currStmt instanceof HttpServiceDefStmt) {
        ((HttpServiceDefStmt) currStmt).registerHttpProcedureTypeProviders(scopedHeap);
      } else if (currStmt instanceof EndpointHandlersBlockStmt) {
        ((EndpointHandlersBlockStmt) currStmt).registerEndpointHandlerProcedureTypeProviders(scopedHeap);
      }
      currStmtListNode = currStmtListNode.tail;
    }
  }

  private void performProcedureTypeValidationPhase(StmtListNode stmtListNode, ScopedHeap scopedHeap) {
    // Very first things first, because we want to allow references of user-defined types anywhere in the scope
    // regardless of what line the type was defined on, we need to first explicitly and pick all of the user-defined
    // type definition stmts and do a first pass of registering their TypeProviders in the symbol table. These
    // TypeProviders will be resolved recursively in the immediately following type-validation phase.
    StmtListNode currStmtListNode = stmtListNode;
    while (currStmtListNode != null) {
      Stmt currStmt = (Stmt) currStmtListNode.getChildren().get(0);
      if (currStmt instanceof ProcedureDefinitionStmt) {
        try {
          currStmt.assertExpectedExprTypes(scopedHeap);
        } catch (ClaroTypeException e) {
          // Java get the ... out of my way and just let me not pollute the interface with a throws modifier.
          // Also let's be fair that I'm just too lazy to make a new RuntimeException version of the ClaroTypeException for
          // use in the execution stage.
          throw new RuntimeException(e);
        }
      }
      currStmtListNode = currStmtListNode.tail;
    }
  }

  private void performModuleDiscoveryPhase(StmtListNode stmtListNode, ScopedHeap scopedHeap) {
    // We need to register the bindings that each ModuleDefinitionStmt provides so that, later,
    // we're ready to handle potential composition of these Modules via using clauses.
    StmtListNode currStmtListNode = stmtListNode;
    while (currStmtListNode != null) {
      Stmt currStmt = (Stmt) currStmtListNode.getChildren().get(0);
      if (currStmt instanceof ModuleDefinitionStmt) {
        try {
          ((ModuleDefinitionStmt) currStmt).registerAssertedBoundKeys(scopedHeap);
        } catch (ClaroTypeException e) {
          // Java get the ... out of my way and just let me not pollute the interface with a throws modifier.
          // Also let's be fair that I'm just too lazy to make a new RuntimeException version of the ClaroTypeException for
          // use in the execution stage.
          throw new RuntimeException(e);
        }
      }
      currStmtListNode = currStmtListNode.tail;
    }
  }

  private void performModuleTypeValidationPhase(StmtListNode stmtListNode, ScopedHeap scopedHeap) {
    // We need to register the bindings that each ModuleDefinitionStmt provides so that, later,
    // we're ready to handle potential composition of these Modules via using clauses.
    StmtListNode currStmtListNode = stmtListNode;
    while (currStmtListNode != null) {
      Stmt currStmt = (Stmt) currStmtListNode.getChildren().get(0);
      if (currStmt instanceof ModuleDefinitionStmt) {
        try {
          ((ModuleDefinitionStmt) currStmt).assertExpectedExprTypes(scopedHeap);
        } catch (ClaroTypeException e) {
          // Java get the ... out of my way and just let me not pollute the interface with a throws modifier.
          // Also let's be fair that I'm just too lazy to make a new RuntimeException version of the ClaroTypeException for
          // use in the execution stage.
          throw new RuntimeException(e);
        }
      }
      currStmtListNode = currStmtListNode.tail;
    }
  }

  /**
   * In some ways this hardcoded class is basically a standard library for this language.
   *
   * @param stmtListJavaSource
   * @return
   */
  // TODO(steving) Take a higher order structure than just a list for the body, allow the java generation steps to
  // TODO(steving) specify code gen for different parts of the gen'd java file. This is just necessary for hacking
  // TODO(steving) java's nuances as our underlying VM.
  private StringBuilder genJavaSource(Node.GeneratedJavaSource stmtListJavaSource) {
    return new StringBuilder(
        String.format(
            "/*******AUTO-GENERATED: DO NOT MODIFY*******/\n\n" +
            "%s" +
            "\n" +
            "import static com.claro.stdlib.Exec.exec;\n" +
            "import static com.claro.intermediate_representation.types.impls.builtins_impls.http.$ClaroHttpResponse.getOk200HttpResponseForHtml;\n" +
            "import static com.claro.intermediate_representation.types.impls.builtins_impls.http.$ClaroHttpResponse.getOk200HttpResponseForJson;\n" +
            "import static com.claro.runtime_utilities.http.$ClaroHttpServer.startServerAndAwaitShutdown;\n" +
            "import static com.claro.stdlib.userinput.UserInput.promptUserInput;\n" +
            "\n" +
            "import com.claro.intermediate_representation.types.BaseType;\n" +
            "import com.claro.intermediate_representation.types.ConcreteType;\n" +
            "import com.claro.intermediate_representation.types.SupportsMutableVariant;\n" +
            "import com.claro.intermediate_representation.types.Type;\n" +
            "import com.claro.intermediate_representation.types.TypeProvider;\n" +
            "import com.claro.intermediate_representation.types.Types;\n" +
            "import com.claro.intermediate_representation.types.impls.ClaroTypeImplementation;\n" +
            "import com.claro.intermediate_representation.types.impls.builtins_impls.*;\n" +
            "import com.claro.intermediate_representation.types.impls.builtins_impls.collections.*;\n" +
            "import com.claro.intermediate_representation.types.impls.builtins_impls.futures.ClaroFuture;\n" +
            "import com.claro.intermediate_representation.types.impls.builtins_impls.http.$ClaroHttpResponse;\n" +
            "import com.claro.intermediate_representation.types.impls.builtins_impls.procedures.ClaroConsumerFunction;\n" +
            "import com.claro.intermediate_representation.types.impls.builtins_impls.procedures.ClaroFunction;\n" +
            "import com.claro.intermediate_representation.types.impls.builtins_impls.procedures.ClaroProviderFunction;\n" +
            "import com.claro.intermediate_representation.types.impls.builtins_impls.structs.ClaroStruct;\n" +
            "import com.claro.intermediate_representation.types.impls.user_defined_impls.$UserDefinedType;\n" +
            "import com.claro.intermediate_representation.types.impls.user_defined_impls.ClaroUserDefinedTypeImplementation;\n" +
            "import com.claro.runtime_utilities.ClaroRuntimeUtilities;\n" +
            "import com.claro.runtime_utilities.http.$ClaroHttpServer;\n" +
            "import com.claro.runtime_utilities.http.$HttpUtil;\n" +
            "import com.claro.runtime_utilities.injector.Injector;\n" +
            "import com.claro.runtime_utilities.injector.Key;\n" +
            "import com.claro.stdlib.userinput.UserInput;\n" +
            "import com.google.auto.value.AutoValue;\n" +
            "import com.google.common.collect.ImmutableList;\n" +
            "import com.google.common.collect.ImmutableMap;\n" +
            "import com.google.common.util.concurrent.Futures;\n" +
            "import com.google.common.util.concurrent.ListenableFuture;\n" +
            "import java.io.StringReader;\n" +
            "import java.util.ArrayList;\n" +
            "import java.util.List;\n" +
            "import java.util.Optional;\n" +
            "import java.util.concurrent.ExecutionException;\n" +
            "import java.util.function.Function;\n" +
            "import java.util.function.Supplier;\n" +
            "import java.util.stream.Collectors;\n" +
            "import lombok.Builder;\n" +
            "import lombok.Data;\n" +
            "import lombok.EqualsAndHashCode;\n" +
            "import lombok.ToString;\n" +
            "import lombok.Value;\n" +
            "import okhttp3.ResponseBody;\n" +
            "import retrofit2.http.GET;\n" +
            "import retrofit2.http.Path;\n" +
            "\n\n" +
            "public class %s {\n" +
            "// Static preamble statements first thing.\n" +
            "%s\n\n" +
            "// Now the static definitions.\n" +
            "%s\n\n" +
            "  public static void main(String[] args) {\n" +
            "    try {\n" +
            "%s\n\n" +
            "    } finally {\n" +
            "      // Because Claro has native support for Graph Functions which execute concurrently/asynchronously,\n" +
            "      // we also need to make sure to shutdown the executor service at the end of the run to clean up.\n" +
            "      ClaroRuntimeUtilities.$shutdownAndAwaitTermination(ClaroRuntimeUtilities.DEFAULT_EXECUTOR_SERVICE);\n" +
            "\n\n" +
            "      // Because Claro has native support for Http Requests sent asynchronously on a threadpool, I\n" +
            "      // need to also ensure that the OkHttp3 client is shutdown.\n" +
            "      $HttpUtil.shutdownOkHttpClient();\n" +
            "    }\n" +
            "  }\n\n" +
            "}",
            this.packageString,
            this.generatedClassName,
            stmtListJavaSource.optionalStaticPreambleStmts().orElse(new StringBuilder()),
            stmtListJavaSource.optionalStaticDefinitions().orElse(new StringBuilder()),
            stmtListJavaSource.javaSourceBody()
        )
    );
  }
}
