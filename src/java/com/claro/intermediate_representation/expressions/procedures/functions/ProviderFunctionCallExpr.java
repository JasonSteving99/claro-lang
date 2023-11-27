package com.claro.intermediate_representation.expressions.procedures.functions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.ProcedureDefinitionStmt;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ProviderFunctionCallExpr extends Expr {
  private final Optional<String> optionalOriginatingDepModuleName;
  protected String functionName;
  private final String originalName;
  protected boolean hashNameForCodegen;
  private Type assertedOutputTypeForGenericFunctionCallUse;
  private Optional<ImmutableList<Type>> optionalConcreteGenericTypeParams = Optional.empty();

  public ProviderFunctionCallExpr(String functionName, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.optionalOriginatingDepModuleName = Optional.empty();
    this.functionName = functionName;
    this.originalName = functionName;
  }

  public ProviderFunctionCallExpr(String depModuleName, String name, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.optionalOriginatingDepModuleName = Optional.of(depModuleName);
    // In order to bring this external procedure into the current compilation unit's scope, this procedure would have
    // been placed in the symbol using a prefixing scheme so as to disambiguate from any other name present in this
    // current compilation unit. Handle that renaming here (this can be undone later at codegen time).
    String disambiguatedName = String.format("$DEP_MODULE$%s$%s", depModuleName, name);
    this.functionName = disambiguatedName;
    this.originalName = disambiguatedName;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    // We simply want to make note of the type that was asserted on this function call for the sake of
    // GENERIC FUNCTION CALLS ONLY. For all other concrete function calls this will be ignored since we
    // actually definitively know the return type from concrete function calls.
    this.assertedOutputTypeForGenericFunctionCallUse = expectedExprType;
    super.assertExpectedExprType(scopedHeap, expectedExprType);
    Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages = Optional.empty();
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Make sure we check this will actually be a valid reference before we allow it.
    Preconditions.checkState(
        scopedHeap.isIdentifierDeclared(this.functionName),
        "No function <%s> within the current scope!",
        this.functionName
    );
    Type referencedIdentifierType = scopedHeap.getValidatedIdentifierType(this.functionName);
    Preconditions.checkState(
        // Include *_FUNCTION just so that later we can throw a more specific error for that case.
        ImmutableSet.of(BaseType.FUNCTION, BaseType.PROVIDER_FUNCTION, BaseType.CONSUMER_FUNCTION)
            .contains(referencedIdentifierType.baseType()),
        "Non-function %s %s cannot be called!",
        referencedIdentifierType,
        this.functionName
    );
    Preconditions.checkState(
        ((Types.ProcedureType) referencedIdentifierType).hasReturnValue(),
        "%s %s does not return a value, it cannot be used as an expression!",
        referencedIdentifierType,
        this.functionName
    );
    Preconditions.checkState(
        !((Types.ProcedureType) referencedIdentifierType).hasArgs(),
        "Expected %s args for function %s but none given!",
        ((Types.ProcedureType) referencedIdentifierType).getArgTypes().size(),
        this.functionName
    );

    Type calledFunctionReturnType = ((Types.ProcedureType.ProviderType) referencedIdentifierType).getReturnType();

    if (((Types.ProcedureType.ProviderType) referencedIdentifierType).getGenericProcedureArgNames().isPresent()) {
      // It's invalid to call a Generic Provider without a contextually asserted type output type.
      if (this.assertedOutputTypeForGenericFunctionCallUse == null) {
        // In this case the program is ambiguous when the return type isn't asserted. The output type of this particular
        // Contract procedure call happens to be composed around a Contract type param that is NOT present in one of the
        // other args, therefore making Contract Impl inference via the arg types alone impossible.
        throw ClaroTypeException.forGenericProviderCallWithoutRequiredContextualOutputTypeAssertion(
            referencedIdentifierType,
            this.functionName,
            calledFunctionReturnType
        );
      }
      // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
      // !WARNING! CONSIDER THE ENTIRE BELOW SECTION AS CONCEPTUALLY A SINGLE INLINED FUNCTION CALL. EDIT AS A UNIT.
      //
      // This hackery is a workaround for Java not supporting true reference semantics to allowing variables to be
      // updated by called functions, and also not allowing painless multiple returns. So these AtomicReferences are
      // used as a workaround to simulate some sort of "out params". All of this only exists because I need this exact
      // same behavior in the implementations of FunctionCallExpr/ProviderFunctionCallExpr/ConsumerFunctionCallStmt.
      // Take this as another example of where Java's concept of hierarchical type structures is a fundamentally broken
      // design (Consumer calls are clearly a Stmt, but really it should share behavior w/ the Provider/Function call
      // Exprs but yet there's no way to model that relationship with a type hierarchy).
      // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
      AtomicReference<Type> calledFunctionReturnType_OUT_PARAM = new AtomicReference<>(calledFunctionReturnType);
      AtomicReference<Types.ProcedureType> referencedIdentifierType_OUT_PARAM =
          new AtomicReference<>((Types.ProcedureType) referencedIdentifierType);
      AtomicReference<String> procedureName_OUT_PARAM = new AtomicReference<>(this.functionName);
      AtomicReference<Optional<ImmutableList<Type>>> optionalConcreteGenericTypeParams_OUT_PARAM =
          new AtomicReference<>(this.optionalConcreteGenericTypeParams);
      try {
        FunctionCallExpr.validateGenericProcedureCall(
            Optional.of(this),
            Optional.of(this.assertedOutputTypeForGenericFunctionCallUse),
            /*argExprs=*/ ImmutableList.of(),
            scopedHeap,
            // "Out params".
            calledFunctionReturnType_OUT_PARAM,
            referencedIdentifierType_OUT_PARAM,
            procedureName_OUT_PARAM,
            optionalConcreteGenericTypeParams_OUT_PARAM
        );
      } finally {
        // Accumulate side effects from the call above regardless of whether it ended up throwing some exception.
        calledFunctionReturnType = calledFunctionReturnType_OUT_PARAM.get();
        referencedIdentifierType = referencedIdentifierType_OUT_PARAM.get();
        this.functionName = procedureName_OUT_PARAM.get();
        this.optionalConcreteGenericTypeParams = optionalConcreteGenericTypeParams_OUT_PARAM.get();
      }
    }

    // Validate that the procedure has been called in a scope that provides the correct bindings.
    // We only care about referencing top-level functions, not any old function (e.g. not lambdas or func refs).
    // Pass in the original func name called so that for the case of monomorphized generic funcs we have a single
    // source of truth.
    FunctionCallExpr.validateNeededBindings(this.originalName, referencedIdentifierType, scopedHeap);

    // If this happens to be a call to a blocking procedure within another procedure definition, we need to
    // propagate the blocking annotation. In service of Claro's goal to provide "Fearless Concurrency" through Graph
    // Procedures, any procedure that can reach a blocking operation is marked as blocking so that we can prevent its
    // usage from Graph Functions.
    if (InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.isPresent()) {
      if (((Types.ProcedureType) referencedIdentifierType).getAnnotatedBlocking()) {
        ((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.get())
            .resolvedProcedureType.getIsBlocking().set(true);
      }
    }

    // Now that everything checks out, go ahead and mark the function used to satisfy the compiler checks.
    scopedHeap.markIdentifierUsed(this.functionName);

    return calledFunctionReturnType;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    // Determine right away if this is going to be a static procedure call (meaning no indirection via a first-class
    // procedure reference.
    boolean isStatic = scopedHeap.getIdentifierData(this.functionName).isStaticValue;

    // TODO(steving) It would honestly be best to ensure that the "unused" checking ONLY happens in the type-checking
    // TODO(steving) phase, rather than having to be redone over the same code in the javasource code gen phase.
    // It's possible that during the process of monomorphization when we are doing type checking over a particular
    // signature, this function call might represent the identification of a new signature for a generic function that
    // needs monomorphization. In that case, this function's identifier may not be in the scoped heap yet and that's ok.
    Optional<String> optionalNormalizedOriginatingDepModulePrefix =
        this.optionalOriginatingDepModuleName
            // Turns out I need to codegen the Java namespace of the dep module.
            .map(depMod -> {
              SerializedClaroModule.UniqueModuleDescriptor depModDescriptor =
                  ScopedHeap.currProgramDepModules.get(depMod, /*isUsed=*/true);
              return String.format(
                  "%s.%s.",
                  depModDescriptor.getProjectPackage(),
                  depModDescriptor.getUniqueModuleName()
              );
            });
    Optional<String> hashedName = Optional.empty();
    if (!this.functionName.contains("$MONOMORPHIZATION")) {
      scopedHeap.markIdentifierUsed(this.functionName);
    } else {
      this.hashNameForCodegen = true;
      if (this.optionalOriginatingDepModuleName.isPresent()) {
        // Additionally, b/c this is a call to a monomorphization, if the procedure is actually located in a dep module,
        // that means that the monomorphization can't be guaranteed to *actually* have been generated in the dep
        // module's codegen. This is b/c all Claro Modules are compiled in isolation - meaning they don't know how
        // they'll be used by any potential future callers. This has the unfortunate side-effect on generic procedures
        // exported by Modules needing to be codegen'd by the callers rather than at the definition. So, here we'll go
        // ahead and drop the namespacing dep$module.foo(...) -> this$module$dep$module$MONOMORPHIZATIONS.foo(...) so that we can reference the local codegen.
        optionalNormalizedOriginatingDepModulePrefix =
            Optional.of(
                String.format(
                    "$MONOMORPHIZATION$%s$%s.",
                    ScopedHeap.getDefiningModuleDisambiguator(Optional.of(this.optionalOriginatingDepModuleName.get())),
                    (hashedName = Optional.of(getHashedName())).get()
                ));
      }
    }

    if (this.hashNameForCodegen) {
      // In order to call the actual monomorphization, we need to ensure that the name isn't too long for Java.
      // So, we're following a hack where all monomorphization names are sha256 hashed to keep them short while
      // still unique.
      this.functionName = hashedName.orElseGet(this::getHashedName);
    }

    StringBuilder res;
    if (isStatic) {
      String procName =
          this.optionalOriginatingDepModuleName
              .map(depMod -> this.functionName.replace(String.format("$DEP_MODULE$%s$", depMod), ""))
              .orElse(this.functionName);
      res = new StringBuilder(String.format(
          "%s$%s.%s()", optionalNormalizedOriginatingDepModulePrefix.orElse(""), procName, procName));
    } else {
      res = new StringBuilder(String.format(
          "%s%s.apply()",
          optionalNormalizedOriginatingDepModulePrefix.orElse(""),
          this.optionalOriginatingDepModuleName
              .map(depMod -> this.functionName.replace(String.format("$DEP_MODULE$%s$", depMod), ""))
              .orElse(this.functionName)
      ));
    }

    // This node will be potentially reused assuming that it is called within a Generic function that gets
    // monomorphized as that process will reuse the exact same nodes over multiple sets of types. So reset
    // the name now.
    this.functionName = this.originalName;

    return res;
  }

  private String getHashedName() {
    return String.format(
        "%s__%s",
        this.optionalOriginatingDepModuleName
            .map(depMod -> this.originalName.replace(String.format("$DEP_MODULE$%s$", depMod), ""))
            .orElse(this.originalName),
        Hashing.sha256().hashUnencodedChars(this.functionName).toString()
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((Types.ProcedureType.ProcedureWrapper) scopedHeap.getIdentifierValue(this.functionName))
        .apply(scopedHeap);
  }
}
