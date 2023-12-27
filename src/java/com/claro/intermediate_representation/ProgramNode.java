package com.claro.intermediate_representation;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.compiler_backends.java_source.monomorphization.MonomorphizationCoordinator;
import com.claro.compiler_backends.java_source.monomorphization.proto.ipc_protos.IPCMessages;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.procedures.functions.StructuralConcreteGenericTypeValidationUtil;
import com.claro.intermediate_representation.statements.*;
import com.claro.intermediate_representation.statements.contracts.ContractDefinitionStmt;
import com.claro.intermediate_representation.statements.contracts.ContractImplementationStmt;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureImplementationStmt;
import com.claro.intermediate_representation.statements.user_defined_type_def_stmts.*;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.google.common.collect.*;
import com.google.common.hash.Hashing;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProgramNode {
  private final String packageString, generatedClassName;
  public StmtListNode stmtListNode;
  public static final Stack<Runnable> miscErrorsFound = new Stack<>();
  public static ImmutableList<ProgramNode> nonMainFiles = ImmutableList.of();
  public static ImmutableList<ContractDefinitionStmt> importedContractDefinitionStmts;
  public static Optional<ModuleNode> moduleApiDef = Optional.empty();
  public static ImmutableSetMultimap<String, SerializedClaroModule.ExportedFlagDefinitions.ExportedFlag>
      transitiveExportedFlags;
  public static ImmutableMap<String, String> resourcesByName;

  // By default, don't support any StdLib.
  private Function<ScopedHeap, ImmutableList<Stmt>> setupStdLibFn = s -> ImmutableList.of();

  // TODO(steving) package and generatedClassName should probably be injected some cleaner way since this is a Target::JAVA_SOURCE-only artifact.
  public ProgramNode(
      StmtListNode stmtListNode,
      String packageString,
      String generatedClassName) {
    this.stmtListNode = stmtListNode;
    this.packageString = packageString;
    this.generatedClassName = generatedClassName;
    InternalStaticStateUtil.optionalGeneratedClassName = Optional.of(generatedClassName);

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

  private void runPhaseOverAllProgramFiles(Consumer<ProgramNode> runPhaseFn) {
    // First run through the non-main src files.
    for (ProgramNode currNonMainProgramNode : ProgramNode.nonMainFiles) {
      runPhaseFn.accept(currNonMainProgramNode);
    }
    // Then finally apply to *this* src file which is implied to be the "main" file.
    runPhaseFn.accept(this);
  }

  // TODO(steving) This method needs to be refactored and have lots of its logic lifted up out into the callers which
  // TODO(steving) are the actual CompilerBackend's. Most of what's going on here is legit not an AST node's responsibility.
  public StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // RUN ALL PHASES THAT PRECEDE TYPE VALIDATION. THESE PHASES PREPROCESS THE GIVEN PROGRAM TO "DISCOVER" AND REGISTER
    // ALL TYPES AND IDENTIFIERS THAT WILL BE NECESSARY DURING THE FOLLOWING TYPE VALIDATION PHASES.
    runDiscoveryCompilationPhases(scopedHeap);

    // MODULE TYPE VALIDATION PHASE:
    runPhaseOverAllProgramFiles(p -> p.performModuleTypeValidationPhase(p.stmtListNode, scopedHeap));

    // TRANSITIVE EXPORTED FLAGS VALIDATION PHASE:
    performTransitiveFlagDefsValidationPhase();

    // STATIC VALUE PROVIDER VALIDATION PHASE:
    if (ProgramNode.moduleApiDef.isPresent()) {
      performStaticValueProviderValidationPhase(scopedHeap);
    }

    // PROCEDURE TYPE VALIDATION PHASE:
    runPhaseOverAllProgramFiles(p -> p.performProcedureTypeValidationPhase(p.stmtListNode, scopedHeap));

    // CONTRACT TYPE VALIDATION PHASE:
    runPhaseOverAllProgramFiles(p -> p.performContractTypeValidationPhase(p.stmtListNode, scopedHeap));

    // GENERIC PROCEDURE TYPE VALIDATION PHASE:
    runPhaseOverAllProgramFiles(p -> p.performGenericProcedureTypeValidationPhase(p.stmtListNode, scopedHeap));
    InternalStaticStateUtil.GnericProcedureDefinitionStmt_doneWithGenericProcedureTypeValidationPhase = true;

    // Now, force the ScopedHeap into a new Scope, because we want to make it explicit that top-level function
    // definitions live in their own scope and cannot reference variables below. We consider functions defined
    // within contract implementations still as top-level functions.
    scopedHeap.enterNewScope();

    // NON-PROCEDURE/MODULE STATEMENT TYPE VALIDATION PHASE:
    // Validate all types in the entire remaining AST before execution.
    if (ProgramNode.moduleApiDef.isPresent()) {
      // Since we're compiling this source code against a module api, it may actually turn out that there are newtype
      // defs exported by the module whose constructors require type checking.
      for (NewTypeDefStmt exportedNewTypeDef : ProgramNode.moduleApiDef.get().exportedNewTypeDefs) {
        try {
          exportedNewTypeDef.assertExpectedExprTypes(scopedHeap);
        } catch (ClaroTypeException e) {
          throw new RuntimeException(e);
        }
      }
      for (AliasStmt exportedAliasDef : ProgramNode.moduleApiDef.get().exportedAliasDefs) {
        try {
          exportedAliasDef.assertExpectedExprTypes(scopedHeap);
        } catch (ClaroTypeException e) {
          throw new RuntimeException(e);
        }
      }
    }
    runPhaseOverAllProgramFiles(
        p -> {
          try {
            // TODO(steving) Currently, GenericProcedureDefinitionStmts are getting type checked a second time here for no reason.
            p.stmtListNode.assertExpectedExprTypes(scopedHeap);
          } catch (ClaroTypeException e) {
            // Java get the ... out of my way and just let me not pollute the interface with a throws modifier.
            // Also let's be fair that I'm just too lazy to make a new RuntimeException version of the ClaroTypeException for
            // use in the execution stage.
            throw new RuntimeException(e);
          }
        }
    );

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
        ScopedHeap.checkAllDepModulesUsed();
      } catch (Exception e) {
        miscErrorsFound.push(() -> System.err.println(e.getMessage()));
      }
    }

    // MODULE API VALIDATION PHASE:
    // Here, in the case that this program is being compiled as a Module, then I must validate that the Module API is
    // actually being correctly satisfied by the given implementation files that were just validated.
    if (ProgramNode.moduleApiDef.isPresent()) {
      try {
        ProgramNode.moduleApiDef.get().assertOpaqueTypesDefinedInternally(scopedHeap);
        ProgramNode.moduleApiDef.get().assertExpectedProceduresActuallyExported(scopedHeap);
        ProgramNode.moduleApiDef.get().assertExpectedContractImplementationsActuallyExported(scopedHeap);
        // Here we validate that if this module api actually references any types from dep modules that the dep module
        // is *actually* listed as an `exports` dep.
        ProgramNode.moduleApiDef.get().assertDepModulesTransitiveTypeExportsActuallyExported();
      } catch (ClaroTypeException e) {
        miscErrorsFound.push(() -> System.err.println(e.getMessage()));
      } finally {
        ProgramNode.moduleApiDef.get().errorMessages.forEach(
            errMsg -> miscErrorsFound.push(() -> System.err.println(errMsg))
        );
      }
    }

    // CODE GEN PHASE:
    // Refuse to do code-gen phase if there were any type validation errors.
    StringBuilder res = null; // I hate null but am also too lazy right now to refactor to Optional<StringBuilder>
    if (Expr.typeErrorsFound.isEmpty() && miscErrorsFound.isEmpty()) {
      // Begin codegen on all non-main src files.
      Node.GeneratedJavaSource programJavaSource = Node.GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
      if (ProgramNode.moduleApiDef.isPresent()) {
        // Since we're compiling this source code against a module api, start by doing codegen for any exported static
        // value definitions, so that static initialization later won't run into any "forward declaration" issues.
        for (FlagDefStmt flagDefStmt : ProgramNode.moduleApiDef.get().exportedFlagDefs) {
          programJavaSource = programJavaSource.createMerged(flagDefStmt.generateJavaSourceOutput(scopedHeap));
        }
        for (StaticValueDefStmt staticValueDefStmt : ProgramNode.moduleApiDef.get().exportedStaticValueDefs) {
          programJavaSource = programJavaSource.createMerged(staticValueDefStmt.generateJavaSourceOutput(scopedHeap));
        }
        // It may turn out that there are newtype defs exported by the module whose constructors require codegen.
        for (NewTypeDefStmt exportedNewTypeDef : ProgramNode.moduleApiDef.get().exportedNewTypeDefs) {
          programJavaSource = programJavaSource.createMerged(exportedNewTypeDef.generateJavaSourceOutput(scopedHeap));
        }
        for (HttpServiceDefStmt exportedHttpServiceDefStmt : ProgramNode.moduleApiDef.get().exportedHttpServiceDefs) {
          programJavaSource =
              programJavaSource.createMerged(exportedHttpServiceDefStmt.generateJavaSourceOutput(scopedHeap));
        }
      }
      for (ProgramNode currNonMainProgramNode : ProgramNode.nonMainFiles) {
        programJavaSource = programJavaSource.createMerged(
            currNonMainProgramNode.stmtListNode.generateJavaSourceOutput(scopedHeap, this.generatedClassName));
        // Drop all javaSourceBody's from each because we actually don't want anything from non-main src files except
        // for things like type/procedure defs.
        programJavaSource.javaSourceBody().setLength(0);
      }
      // Make sure to codegen any potential dynamic dispatch handlers from dep contract defs.
      for (ContractDefinitionStmt importedContractDefinitionStmt : ProgramNode.importedContractDefinitionStmts) {
        programJavaSource =
            programJavaSource.createMerged(importedContractDefinitionStmt.generateJavaSourceOutput(scopedHeap));
      }
      // Now do codegen on this current program, implied to be the "main" src file. Do NOT throw away the javaSourceBody
      // on this main src file as this is the actual "program" that the programmer wants to be able to run.
      programJavaSource =
          programJavaSource.createMerged(stmtListNode.generateJavaSourceOutput(scopedHeap, this.generatedClassName));
      // Just before committing to this codegen result, in the case that this is actually a Module definition being
      // compiled, the "main" file is actually a dummy file, so drop its main stmts.
      if (ProgramNode.moduleApiDef.isPresent()) {
        programJavaSource.javaSourceBody().setLength(0);
      }
      // Finally, wrap up the GeneratedJavaSource as a Java src file.
      res = genJavaSource(programJavaSource);

      // As a final step, it's possible that this compilation unit depended on some dep module for a generic procedure(s)
      // whose monomorphization(s) will still need to be generated. Do that now and append the codegen to the codegen
      // for the current compilation unit.
      // TODO(steving) This has resulted in a very significant amount of codegen duplication for the sake of repeatedly
      //  monomorphizing generic procedures for the same concrete type params in different compilation units. Some more
      //  sophisticated approach that avoids code duplication while maintaining build incrementality will be necessary
      //  to get Claro to a more practically useful place.
      if (!InternalStaticStateUtil.JavaSourceCompilerBackend_depModuleGenericMonomoprhizationsNeeded.isEmpty()) {
        // Sequentially trigger all dep module monomorphizations. Register all of the monomorphizations first and then
        // collect them afterwards. It's necessary to do it this way since each monomorphization request may actually
        // trigger an unknown chain of other monomorphization requests even from transitive dep modules, so a single
        // monomorphization request doesn't actually correspond directly to something I can immediately append to codegen.
        for (Map.Entry<String, IPCMessages.MonomorphizationRequest> depModuleMonomorphization :
            InternalStaticStateUtil.JavaSourceCompilerBackend_depModuleGenericMonomoprhizationsNeeded.entries()) {
          // Under the hood this call is abstracting away a massive amount of multiprocessing complexity.
          MonomorphizationCoordinator.getDepModuleMonomorphization(
              ScopedHeap.getDefiningModuleDisambiguator(Optional.of(depModuleMonomorphization.getKey())),
              depModuleMonomorphization.getValue()
          );
        }
        res.append("\n// Dep Module Monomorphizations Generated Below:\n");
        for (String depModule : MonomorphizationCoordinator.monomorphizationsByModuleAndRequestCache.rowKeySet()) {
          for (Map.Entry<IPCMessages.MonomorphizationRequest, String> entry
              : MonomorphizationCoordinator.monomorphizationsByModuleAndRequestCache.row(depModule).entrySet()) {
            IPCMessages.MonomorphizationRequest monomorphizationRequest = entry.getKey();
            String monomorphizationCodegen = entry.getValue();
            String genProcName =
                String.format(
                    "%s__%s",
                    monomorphizationRequest.getProcedureName(),
                    Hashing.sha256().hashUnencodedChars(
                        ContractProcedureImplementationStmt.getCanonicalProcedureName(
                            "$MONOMORPHIZATION",
                            monomorphizationRequest.getConcreteTypeParamsList().stream()
                                .map(Types::parseTypeProto).collect(ImmutableList.toImmutableList()),
                            monomorphizationRequest.getProcedureName()
                        ))
                );
            res.append("/*MONOMORPHIZATION: ").append(depModule).append("*/\n")
                .append("final class $MONO$")
                .append(Hashing.sha256().hashUnencodedChars(depModule))
                .append('$')
                .append(genProcName)
                .append(" {\n");
            res.append(monomorphizationCodegen).append("\n");
            res.append("\n}\n");
          }
        }

        // Cleanup any threads or subprocesses that got started up by monomorphization.
        MonomorphizationCoordinator.shutdownDepModuleMonomorphization();
      }
      res.append("\n// Semantically Polymorphic Builtin Type Concrete Monomorphizations Generated Below:\n");
      for (Types.StructType s : Types.StructType.allReferencedConcreteStructTypesToOptionalGenericTypeMappings.keySet()) {
        res.append(s.getConcreteJavaClassRepresentation());
      }
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

  // Run "discovery" prework over the current compilation unit so that type validation is ready to be performed after.
  // Note: this has really only been factored out so that in the case of dep module monomorphization, this prework can
  //       be run in isolation, only re-triggering type validation of generic procedures that are explicitly requested
  //       by the monomorphization coordinator (as well as for transitive deps on local generic procedures).
  public void runDiscoveryCompilationPhases(ScopedHeap scopedHeap) {
    // Setup the StdLib in the current Scope and append any setup Stmts to prefix the given program.
    setupStdLib(scopedHeap);

    // Setup any Resources that the user registered in the current compilation unit's build target.
    for (Map.Entry<String, String> resourceByName : ProgramNode.resourcesByName.entrySet()) {
      String disambiguatedResourceName =
          String.format(
              "%s$%s",
              resourceByName.getKey(),
              ScopedHeap.getDefiningModuleDisambiguator(Optional.of("resources"))
          );
      scopedHeap.observeStaticIdentifierValue(
          disambiguatedResourceName,
          Types.RESOURCE_TYPE_CONSTRUCTOR.apply(resourceByName.getKey(), resourceByName.getValue()).apply(scopedHeap),
          resourceByName.getValue(),
          /*isLazy=*/false
      );
      scopedHeap.initializeIdentifier(disambiguatedResourceName);
    }

    // TODO(steving) These Type + Procedure Discovery phases do things in O(2n) time, we really should structure
    // TODO(steving) the response from the parser better so that it's not just a denormalized list of stmts,
    // TODO(steving) instead it should give a structured list of type defs seperate from procedure defs etc.
    // TYPE DISCOVERY PHASE:
    if (ProgramNode.moduleApiDef.isPresent()) {
      // Since we're compiling this source code against a module api, it may actually turn out that there are newtype
      // defs exported by the module that should also be accessible w/in its implementation sources.
      ProgramNode.moduleApiDef.get().registerExportedTypeDefs(scopedHeap);
      // Now, since all the types defined by this and dep modules are all known, time to validate that the initializers
      // and unwrappers are only defined for valid user-defined types exported by *this* module.
      ProgramNode.moduleApiDef.get()
          .assertInitializersAndUnwrappersBlocksAreDefinedOnTypesExportedByThisModule(scopedHeap);
    }
    runPhaseOverAllProgramFiles(p -> p.performTypeDiscoveryPhase(p.stmtListNode, scopedHeap));

    // STATIC VALUE DISCOVERY PHASE:
    if (ProgramNode.moduleApiDef.isPresent()) {
      // Since we're compiling this source code against a module api, it may actually turn out that there are static
      // values exported by the module that should be declared for use within the impl sources. However, the static
      // values will not be initialized yet. We'll use the fact that Claro will error on trying to read uninitialized
      // values to enforce a strict static initialization ordering between dependent static values (unfortunately, this
      // ordering is only configurable by users by changing the order the values are declared in the module api file).
      for (FlagDefStmt flagDefStmt : ProgramNode.moduleApiDef.get().exportedFlagDefs) {
        try {
          flagDefStmt.assertExpectedExprTypes(scopedHeap);
        } catch (ClaroTypeException e) {
          throw new RuntimeException(e);
        }
      }
      for (StaticValueDefStmt staticValueDefStmt : ProgramNode.moduleApiDef.get().exportedStaticValueDefs) {
        try {
          staticValueDefStmt.assertExpectedExprTypes(scopedHeap);
        } catch (ClaroTypeException e) {
          throw new RuntimeException(e);
        }
      }
    }

    // PROCEDURE DISCOVERY PHASE:
    if (ProgramNode.moduleApiDef.isPresent()) {
      // Since we're compiling this source code against a module api, it may actually turn out that there are newtype
      // defs exported by the module whose constructors should also be accessible w/in its implementation sources.
      for (NewTypeDefStmt exportedNewTypeDef : ProgramNode.moduleApiDef.get().exportedNewTypeDefs) {
        exportedNewTypeDef.registerConstructorTypeProvider(scopedHeap);
      }
      // HttpServiceDefStmts also register synthetic procedures for calling the defined service.
      for (HttpServiceDefStmt exportedHttpServiceDefStmt : ProgramNode.moduleApiDef.get().exportedHttpServiceDefs) {
        exportedHttpServiceDefStmt.registerHttpProcedureTypeProviders(scopedHeap);
      }
    }
    runPhaseOverAllProgramFiles(p -> p.performProcedureDiscoveryPhase(p.stmtListNode, scopedHeap));

    // CONTRACT DISCOVERY PHASE:
    if (ProgramNode.moduleApiDef.isPresent()) {
      // Since we're compiling this source code against a module api, it may actually turn out that there are contract
      // defs exported by the module that should also be accessible w/in its implementation sources.
      for (ContractDefinitionStmt contractDef : ProgramNode.moduleApiDef.get().exportedContractDefs) {
        try {
          contractDef.assertExpectedExprTypes(scopedHeap);
        } catch (ClaroTypeException e) {
          throw new RuntimeException(e);
        }
      }
    }
    runPhaseOverAllProgramFiles(p -> p.performContractDiscoveryPhase(p.stmtListNode, scopedHeap));

    // GENERIC PROCEDURE DISCOVERY PHASE:
    runPhaseOverAllProgramFiles(p -> p.performGenericProcedureDiscoveryPhase(p.stmtListNode, scopedHeap));

    // Modules only need to know about procedure type signatures, nothing else, so save procedure type
    // validation for after the full module discovery and validation phases since procedure type validation
    // phase will depend on knowledge of transitive module bindings to validate using-blocks nested in
    // top-level procedures.

    // MODULE DISCOVERY PHASE:
    runPhaseOverAllProgramFiles(p -> p.performModuleDiscoveryPhase(p.stmtListNode, scopedHeap));
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
    InternalStaticStateUtil.GnericProcedureDefinitionStmt_doneWithGenericProcedureTypeValidationPhase = true;

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
      if (currStmt instanceof AtomDefinitionStmt) {
        ((AtomDefinitionStmt) currStmt).registerType(scopedHeap);
      } else if (currStmt instanceof UserDefinedTypeDefinitionStmt) {
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
    for (ContractDefinitionStmt currContract : importedContractDefinitionStmts) {
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
    // Do type validation on all remaining ProcedureDefStmts (note that ProcedureDefStmts automatically short-circuit if
    // their type validation method is called more than once, so this isn't actually duplicating the above work).
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

  private static void performTransitiveFlagDefsValidationPhase() {
    ImmutableSetMultimap<String, String> flagsToUniqueModuleNames =
        ProgramNode.transitiveExportedFlags.entries().stream()
            .collect(ImmutableSetMultimap.toImmutableSetMultimap(
                e -> e.getValue().getName(),
                Map.Entry::getKey
            ));
    HashMap<String, List<String>> duplicatedFlags = Maps.newHashMap();
    for (String flagName : flagsToUniqueModuleNames.keys()) {
      if (flagsToUniqueModuleNames.get(flagName).size() > 1) {
        duplicatedFlags.put(flagName, Lists.newArrayList(flagsToUniqueModuleNames.get(flagName)));
      }
    }
    String currModuleDisambiguator =
        ScopedHeap.getDefiningModuleDisambiguator(Optional.empty());
    ProgramNode.moduleApiDef.ifPresent(m -> m.exportedFlagDefs.forEach(f -> {
      if (flagsToUniqueModuleNames.containsKey(f.identifier.identifier)) {
        duplicatedFlags.compute(
            f.identifier.identifier,
            (unused, uniqueModuleNames) -> {
              uniqueModuleNames.add(currModuleDisambiguator);
              return uniqueModuleNames;
            }
        );
      }
    }));
    if (!duplicatedFlags.isEmpty()) {
      ProgramNode.miscErrorsFound.push(
          () -> System.err.println(ClaroTypeException.forIllegalDuplicateFlagDefsFound(duplicatedFlags).getMessage()));
    }
  }

  private static void performStaticValueProviderValidationPhase(ScopedHeap scopedHeap) {
    // Very first thing, in order to guarantee that static values are correctly initialized, do validation that all
    // exported static values have a corresponding provider function that can be used to initialize the static value.
    ProgramNode.moduleApiDef.get().exportedStaticValueDefs.forEach(
        s -> {
          if (!s.resolvedType.isPresent()) {
            // In this case, it turns out that the type was rejected (because it was mutable). No static providers are
            // necessary for this as an error has already been logged rejecting the static value.
            return;
          }
          Type resolvedStaticValueType = s.resolvedType.get();
          String expectedStaticValueProvider = String.format("static_%s", s.identifier.identifier);
          if (!scopedHeap.isIdentifierDeclared(expectedStaticValueProvider)) {
            // The required static value provider wasn't even defined.
            miscErrorsFound.push(() -> System.err.println(
                ClaroTypeException.forModuleExportedStaticValueProviderNotDefinedInModuleImplFiles(
                    s.identifier.identifier, resolvedStaticValueType).getMessage()));
            // Just to avoid super noisy cascading failures, just mark the static value initialized and move on.
            scopedHeap.initializeIdentifier(s.identifier.identifier);
          } else {
            Type actualStaticValueProviderType = scopedHeap.getValidatedIdentifierType(expectedStaticValueProvider);
            if (!actualStaticValueProviderType.baseType().equals(BaseType.PROVIDER_FUNCTION)
                || !((Types.ProcedureType) actualStaticValueProviderType).getReturnType()
                .equals(resolvedStaticValueType)
                // Additionally, lazy static values aren't allowed to be provided by blocking procedures as this would
                // potentially constitute a corner case that breaks Claro's requirement that blocking procedures aren't
                // called from within concurrent contexts.
                || (!((Types.ProcedureType) actualStaticValueProviderType).getAnnotatedBlocking().equals(false)
                    && s.isLazy)) {
              // The required static value provider was defined with the wrong type.
              miscErrorsFound.push(() -> System.err.println(
                  ClaroTypeException.forModuleExportedStaticValueProviderNameBoundToIncorrectImplementationType(
                      s.identifier.identifier,
                      resolvedStaticValueType,
                      ((Types.ProcedureType) actualStaticValueProviderType).getReturnType(),
                      !((Types.ProcedureType) actualStaticValueProviderType).getAnnotatedBlocking().equals(false)
                  ).getMessage()));
            } else {
              // Good programmer! The required static value provider was implemented. Do type validation on it now.
              try {
                ((Types.ProcedureType) actualStaticValueProviderType).getProcedureDefStmt()
                    .assertExpectedExprTypes(scopedHeap);
              } catch (ClaroTypeException e) {
                throw new RuntimeException(e);
              }
              // Now, finally go ahead and mark the static value initialized manually. This is the only line that is
              // legally allowed to mark a static value initialized. Doing this here enables the guarantee that
              // successive static values can legally depend on one another as long as they are well-ordered.
              scopedHeap.initializeIdentifier(s.identifier.identifier);
            }
          }
        }
    );
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
    String mainMethodCodegen;
    if (ProgramNode.moduleApiDef.isPresent()) {
      // In this case no main method whatsoever is desired. Instead we're simply compiling a static java library class.
      mainMethodCodegen = "";
    } else {
      mainMethodCodegen = String.format(
          "public static void main(String[] args) {\n" +
          "    try {\n" +
          "/**BEGIN USER CODE**/\n" +
          "%s\n\n" +
          "/**END USER CODE**/\n" +
          "    } finally {\n" +
          "      // Because Claro has native support for Graph Functions which execute concurrently/asynchronously,\n" +
          "      // we also need to make sure to shutdown the executor service at the end of the run to clean up.\n" +
          "      ClaroRuntimeUtilities.$shutdownAndAwaitTermination(ClaroRuntimeUtilities.DEFAULT_EXECUTOR_SERVICE);\n" +
          "%s" +
          "    }\n" +
          "  }\n\n",
          stmtListJavaSource.javaSourceBody(),
          // Only actually codegen cleanup code for the optional stdlib `http` Module, if it was actually used somewhere
          // in this Claro program and we actually have runtime Java deps on the module's custom deps.
          ScopedHeap.currProgramDepModules.containsRow("http")
          ? "      // Because Claro has native support for Http Requests sent asynchronously on a threadpool, I\n" +
            "      // need to also ensure that the OkHttp3 client is shutdown.\n" +
            "      com.claro.runtime_utilities.http.$HttpUtil.shutdownOkHttpClient();\n"
          : ""
      );
    }
    StringBuilder staticValueInitialization = new StringBuilder();
    ProgramNode.moduleApiDef.ifPresent(
        m -> m.exportedFlagDefs.forEach(
            s -> s.generateStaticInitialization(staticValueInitialization)
        ));
    ProgramNode.moduleApiDef.ifPresent(
        m -> m.exportedStaticValueDefs.forEach(
            s -> s.generateStaticInitialization(staticValueInitialization)
        ));
    return new StringBuilder(
        String.format(
            "/*******AUTO-GENERATED: DO NOT MODIFY*******/\n\n" +
            "%s" +
            "\n" +
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
            "import com.claro.intermediate_representation.types.impls.builtins_impls.atoms.$ClaroAtom;\n" +
            "import com.claro.intermediate_representation.types.impls.builtins_impls.futures.ClaroFuture;\n" +
            "import com.claro.intermediate_representation.types.impls.builtins_impls.procedures.ClaroConsumerFunction;\n" +
            "import com.claro.intermediate_representation.types.impls.builtins_impls.procedures.ClaroFunction;\n" +
            "import com.claro.intermediate_representation.types.impls.builtins_impls.procedures.ClaroProviderFunction;\n" +
            "import com.claro.intermediate_representation.types.impls.builtins_impls.structs.ClaroStruct;\n" +
            "import com.claro.intermediate_representation.types.impls.user_defined_impls.$UserDefinedType;\n" +
            "import com.claro.intermediate_representation.types.impls.user_defined_impls.ClaroUserDefinedTypeImplementation;\n" +
            "import com.claro.runtime_utilities.ClaroRuntimeUtilities;\n" +
            "import com.claro.runtime_utilities.injector.Injector;\n" +
            "import com.claro.runtime_utilities.injector.Key;\n" +
            "import com.claro.stdlib.userinput.UserInput;\n" +
            "import com.google.common.collect.ImmutableList;\n" +
            "import com.google.common.collect.ImmutableMap;\n" +
            "import com.google.common.collect.ImmutableSet;\n" +
            "import com.google.common.util.concurrent.Futures;\n" +
            "import com.google.common.util.concurrent.ListenableFuture;\n" +
            "import com.google.devtools.common.options.Option;\n" +
            "import com.google.devtools.common.options.OptionsBase;\n" +
            "import java.io.StringReader;\n" +
            "import java.util.ArrayList;\n" +
            "import java.util.List;\n" +
            "import java.util.Optional;\n" +
            "import java.util.concurrent.ExecutionException;\n" +
            "import java.util.function.Function;\n" +
            "import java.util.function.Supplier;\n" +
            "import java.util.stream.Collectors;\n" +
            "\n\n" +
            "@SuppressWarnings(\"unchecked\")\n" +
            "public class %s {\n" +
            "\n" +
            "// This class will be populated with the definition of any flags that are defined to be parsed\n" +
            "// anywhere in the overall program.\n" +
            "%s\n" +
            "// Setup the atom cache so that all atoms are singleton.\n" +
            "public static final $ClaroAtom[] ATOM_CACHE = new $ClaroAtom[]{%s};\n\n" +
            "// Static preamble statements first thing.\n" +
            "%s\n\n" +
            "// Static Initializers.\n" +
            "%s\n\n" +
            "// Now the static definitions.\n" +
            "%s\n\n" +
            "// Optionally the main method will be here if this is not a Module.\n" +
            "%s\n" +
            "}\n",
            this.packageString,
            this.generatedClassName,
            // Only do flag parsing related codegen if we actually need to parse cli flags.
            !ProgramNode.moduleApiDef.isPresent() && !transitiveExportedFlags.isEmpty()
            ? ProgramNode.transitiveExportedFlags.values().stream()
                .map(f ->
                         FlagDefStmt.generateAnnotatedOptionField(
                             f.getName(), Types.parseTypeProto(f.getType())))
                .collect(Collectors.joining(
                    "\n",
                    "public static class $FlagsToParse extends OptionsBase {\n",
                    "\n}\n" +
                    "// Very first thing to do is statically configure the generated class to be used for parsing flags.\n" +
                    "  static {\n    com.claro.runtime_utilities.flags.$Flags.$programOptionsClass = $FlagsToParse.class;\n  }\n"
                ))
            : "",
            AtomDefinitionStmt.codegenAtomCacheInit(),
            stmtListJavaSource.optionalStaticPreambleStmts().orElse(new StringBuilder()),
            staticValueInitialization,
            stmtListJavaSource.optionalStaticDefinitions().orElse(new StringBuilder()),
            mainMethodCodegen
        )
    );
  }
}
