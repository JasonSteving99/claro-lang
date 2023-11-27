package com.claro.intermediate_representation.expressions.procedures.functions;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.ProcedureDefinitionStmt;
import com.claro.intermediate_representation.statements.UsingBlockStmt;
import com.claro.intermediate_representation.statements.contracts.ContractImplementationStmt;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.google.common.hash.Hashing;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FunctionCallExpr extends Expr {
  public Optional<String> optionalOriginatingDepModuleName;
  public String name;
  public boolean hashNameForCodegen = false;
  public boolean staticDispatchCodegen = false;
  public final ImmutableList<Expr> argExprs;
  private Type assertedOutputTypeForGenericFunctionCallUse;
  public String originalName;
  public Optional<String> optionalExtraArgsCodegen = Optional.empty();
  public Optional<ImmutableList<Type>> optionalConcreteGenericTypeParams = Optional.empty();
  private Optional<Type> representsUserDefinedTypeConstructor = Optional.empty();

  public FunctionCallExpr(String name, ImmutableList<Expr> args, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.optionalOriginatingDepModuleName = Optional.empty();
    this.name = name;
    this.originalName = name;
    this.argExprs = args;
  }

  // Use this factory literally just to simplify calls that hinge on optionally setting an originating dep module.
  public static FunctionCallExpr getFunctionCallExprForOptionalOriginatingDepModule(Optional<String> optionalOriginatingDepModuleName, String name, ImmutableList<Expr> args, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    if (optionalOriginatingDepModuleName.isPresent()) {
      return new FunctionCallExpr(optionalOriginatingDepModuleName.get(), name, args, currentLine, currentLineNumber, startCol, endCol);
    }
    return new FunctionCallExpr(name, args, currentLine, currentLineNumber, startCol, endCol);
  }

  public FunctionCallExpr(String depModuleName, String name, ImmutableList<Expr> args, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.optionalOriginatingDepModuleName = Optional.of(depModuleName);
    // In order to bring this external procedure into the current compilation unit's scope, this procedure would have
    // been placed in the symbol using a prefixing scheme so as to disambiguate from any other name present in this
    // current compilation unit. Handle that renaming here (this can be undone later at codegen time).
    String disambiguatedName = String.format("$DEP_MODULE$%s$%s", depModuleName, name);
    this.name = disambiguatedName;
    this.originalName = disambiguatedName;
    this.argExprs = args;
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

  @SuppressWarnings("unchecked")
  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Make sure we check this will actually be a valid reference before we allow it.
    Preconditions.checkState(
        scopedHeap.isIdentifierDeclared(this.name),
        "No function <%s> within the current scope!",
        this.name
    );
    ScopedHeap.IdentifierData referencedIdentifierData = scopedHeap.getIdentifierData(this.name);
    Type referencedIdentifierType = referencedIdentifierData.type;
    // It's possible that this is a default constructor call for a custom type. If it's being called textually "before"
    // the definition of the type, then the type may not actually be resolved yet, so resolve it now.
    if (referencedIdentifierType == null) {
      referencedIdentifierType = TypeProvider.Util.getTypeByName(this.name, true).resolveType(scopedHeap);
    }
    Preconditions.checkState(
        // Include CONSUMER_FUNCTION just so that later we can throw a more specific error for that case.
        ImmutableSet.of(BaseType.FUNCTION, BaseType.PROVIDER_FUNCTION, BaseType.CONSUMER_FUNCTION)
            .contains(referencedIdentifierType.baseType())
        || // UserDefinedType's are also valid as references to default constructors.
        (referencedIdentifierType.baseType().equals(BaseType.USER_DEFINED_TYPE)
         && referencedIdentifierData.isTypeDefinition),
        "Non-function %s %s cannot be called!",
        referencedIdentifierType,
        this.name
    );
    if (referencedIdentifierType.baseType().equals(BaseType.USER_DEFINED_TYPE)) {
      Types.UserDefinedType referencedUserDefinedType = (Types.UserDefinedType) referencedIdentifierType;
      if (InternalStaticStateUtil.InitializersBlockStmt_initializersByInitializedTypeNameAndModuleDisambiguator
              .contains(referencedUserDefinedType.getTypeName(), referencedUserDefinedType.getDefiningModuleDisambiguator())
          &&
          !(InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.isPresent()
            && (InternalStaticStateUtil.InitializersBlockStmt_initializersByInitializedTypeNameAndModuleDisambiguator
                    .get(referencedUserDefinedType.getTypeName(), referencedUserDefinedType.getDefiningModuleDisambiguator())
                    .contains(((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.get()).procedureName)
                ||
                (((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.get())
                     .procedureName.contains("$$MONOMORPHIZATION")
                 &&
                 InternalStaticStateUtil.InitializersBlockStmt_initializersByInitializedTypeNameAndModuleDisambiguator
                     .get(referencedUserDefinedType.getTypeName(), referencedUserDefinedType.getDefiningModuleDisambiguator())
                     .contains(((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.get())
                                   .procedureName
                                   // Monomorphization names are formatted like "$$MONOMORPHIZATION::<TypeParam>___FooFunc";
                                   .substring(
                                       ((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.get())
                                           .procedureName.indexOf(">___") + 4)))
            )
          )
      ) {
        // Actually, it turns out this is an illegal reference to the auto-generated default constructor outside of one
        // of the procedures defined within the `initializers` block.
        // Technically though the types check, so let's log the error and continue to find more errors.
        this.logTypeError(
            ClaroTypeException.forIllegalUseOfUserDefinedTypeDefaultConstructorOutsideOfInitializerProcedures(
                referencedIdentifierType,
                InternalStaticStateUtil.InitializersBlockStmt_initializersByInitializedTypeNameAndModuleDisambiguator
                    .get(referencedUserDefinedType.getTypeName(), referencedUserDefinedType.getDefiningModuleDisambiguator())
            ));
      }
      // Swap out a synthetic constructor function.
      this.representsUserDefinedTypeConstructor =
          Optional.of(scopedHeap.getValidatedIdentifierType(
              (this.name.startsWith("$DEP_MODULE$")
               ? this.name.substring(this.name.lastIndexOf('$') + 1)
                 + "$"
                 + ScopedHeap.getDefiningModuleDisambiguator(
                  Optional.of(this.name.substring("$DEP_MODULE$" .length(), this.name.lastIndexOf('$'))))
               : String.format(
                   "%s$%s",
                   this.name,
                   ScopedHeap.getDefiningModuleDisambiguator(this.optionalOriginatingDepModuleName)
               ))
              + "$wrappedType"));
      // Finally, it may be illegal to instantiate this type if it's actually an opaque type.
      if (this.representsUserDefinedTypeConstructor.get().baseType()
          .equals(BaseType.$SYNTHETIC_OPAQUE_TYPE_WRAPPED_VALUE_TYPE)) {
        // There's actually no definition of the constructor function in this module since there was no procedure to
        // import upon setting up the dep module imports, so log the exception and return.
        logTypeError(
            ClaroTypeException.forIllegalUseOfOpaqueUserDefinedTypeDefaultConstructor(referencedUserDefinedType));
        return Types.UNKNOWABLE;
      }
      this.name = this.name + "$constructor";
      referencedIdentifierType =
          TypeProvider.Util.getTypeByName(this.name, /*isTypeDefinition=*/false).resolveType(scopedHeap);
    }
    Preconditions.checkState(
        ((Types.ProcedureType) referencedIdentifierType).hasArgs(),
        "%s %s does not take any args, it cannot be called with arguments!",
        referencedIdentifierType,
        this.name
    );
    Preconditions.checkState(
        ((Types.ProcedureType) referencedIdentifierType).hasReturnValue(),
        "%s %s does not return a value, it cannot be used as an expression!",
        referencedIdentifierType,
        this.name
    );
    Type calledFunctionReturnType = ((Types.ProcedureType.FunctionType) referencedIdentifierType).getReturnType();
    ImmutableList<Type> definedArgTypes = ((Types.ProcedureType.FunctionType) referencedIdentifierType).getArgTypes();
    int argsCount = definedArgTypes.size();

    // Make sure that we at least do due diligence and first check that we have the right number of args.
    Preconditions.checkState(
        argsCount == this.argExprs.size(),
        "Expected %s args for function %s, but found %s",
        argsCount,
        this.name,
        this.argExprs.size()
    );

    if (((Types.ProcedureType.FunctionType) referencedIdentifierType).getAnnotatedBlocking() == null
        // If the procedure being called is both generic and blocking-generic, then let's handle that all at once
        // in the generic case.
        // TODO(steving) At some point refactor away the special case for only being blocking-generic.
        && !((Types.ProcedureType) referencedIdentifierType).getGenericProcedureArgNames().isPresent()) {
      // This function must be generic over the blocking keyword, so need to see if the call is targeting a concrete
      // type signature for this function.
      ImmutableSet<Integer> blockingGenericArgIndices =
          ((Types.ProcedureType.FunctionType) referencedIdentifierType).getAnnotatedBlockingGenericOverArgs().get();

      // We need to accept whatever inferred type given by the args we're deriving the blocking annotation from.
      // For the rest, we'll assert the known required types.
      boolean isBlocking = false;
      for (int i = 0; i < argsCount; i++) {
        if (blockingGenericArgIndices.contains(i)) {
          // Generically attempting to accept a concrete blocking variant that the user gave for this arg.
          Types.ProcedureType maybeBlockingArgType = (Types.ProcedureType) definedArgTypes.get(i);
          Type concreteArgType =
              getBlockingGenericArgVariantType(scopedHeap, this.argExprs.get(i), maybeBlockingArgType);
          // We'll determine the hard rule, that if even a single of the blocking-generic-args are blocking, then
          // the entire function call is blocking.
          isBlocking = ((Types.ProcedureType) concreteArgType).getAnnotatedBlocking();
        } else {
          // We have to handle the case where the defined arg type is actually a generic param, in which case
          // we're actually currently validating against a generic function definition body and only care that
          // the type we're passing in is also a generic type (since the correct type will be validated at the
          // call site).
          if (definedArgTypes.get(i).baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
            argExprs.get(i).assertExpectedBaseType(scopedHeap, BaseType.$GENERIC_TYPE_PARAM);
          } else {
            // This arg is not being treated as generic, so assert against the static defined type.
            if (definedArgTypes.get(i).baseType().equals(BaseType.ONEOF)) {
              // Since the arg type itself is a oneof, then we actually really need to accept any variant type.
              this.argExprs.get(i).assertSupportedExprOneofTypeVariant(
                  scopedHeap,
                  definedArgTypes.get(i),
                  ((Types.OneofType) definedArgTypes.get(i)).getVariantTypes()
              );
            } else {
              argExprs.get(i).assertExpectedExprType(scopedHeap, definedArgTypes.get(i));
            }
          }
        }
      }

      // From now, we'll stop validating against the generic type signature, and validate against this concrete
      // signature since we know which one we want to use to continue type-checking with now. (Note, this is a
      // bit of a white lie - the blocking concrete variant signature has *all* blocking for the blocking-generic
      // args, which is not necessarily the case for the *actual* args, but this doesn't matter since after this
      // point we're actually done with the args type checking, and we'll move onto codegen where importantly the
      // generated code is all exactly the same across blocking/non-blocking).
      referencedIdentifierType =
          scopedHeap.getValidatedIdentifierType(
              (isBlocking ? "$blockingConcreteVariant_" : "$nonBlockingConcreteVariant_")
              // Refer to the name of the procedure as defined by the owning ProcedureDefinitionStmt rather
              + ((ProcedureDefinitionStmt)
                     ((Types.ProcedureType.FunctionType) referencedIdentifierType)
                         .getProcedureDefStmt()).procedureName);
    } else if (((Types.ProcedureType) referencedIdentifierType).getGenericProcedureArgNames().isPresent()) {
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
      AtomicReference<String> procedureName_OUT_PARAM = new AtomicReference<>(this.name);
      AtomicReference<Optional<ImmutableList<Type>>> optionalConcreteGenericTypeParams_OUT_PARAM =
          new AtomicReference<>(this.optionalConcreteGenericTypeParams);
      try {
        validateGenericProcedureCall(
            Optional.of(this),
            Optional.ofNullable(this.assertedOutputTypeForGenericFunctionCallUse),
            this.argExprs,
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
        this.name = procedureName_OUT_PARAM.get();
        this.optionalConcreteGenericTypeParams = optionalConcreteGenericTypeParams_OUT_PARAM.get();
      }

      // Since this might be a generic type's default constructor, we may need to swap out the generic type for the
      // concrete type of the actual arg that was passed into the constructor. I can just do this unconditionally here
      // since we wouldn't've even gotten here if type checking failed.
      if (this.representsUserDefinedTypeConstructor.isPresent()) {
        this.representsUserDefinedTypeConstructor = Optional.of(this.argExprs.get(0).getValidatedExprType(scopedHeap));
      }
    } else {
      // Validate that all of the given parameter Exprs are of the correct type.
      for (int i = 0; i < this.argExprs.size(); i++) {
        if (definedArgTypes.get(i).baseType().equals(BaseType.ONEOF)) {
          // Since the arg type itself is a oneof, then we actually really need to accept any variant type.
          this.argExprs.get(i).assertSupportedExprOneofTypeVariant(
              scopedHeap,
              definedArgTypes.get(i),
              ((Types.OneofType) definedArgTypes.get(i)).getVariantTypes()
          );
        } else {
          this.argExprs.get(i).assertExpectedExprType(scopedHeap, definedArgTypes.get(i));
        }
      }
    }

    // Validate that the procedure has been called in a scope that provides the correct bindings.
    // We only care about referencing top-level functions, not any old function (e.g. not lambdas or func refs).
    FunctionCallExpr.validateNeededBindings(this.name, referencedIdentifierType, scopedHeap);

    // If this happens to be a call to a blocking procedure within another procedure definition, we need to
    // propagate the blocking annotation. In service of Claro's goal to provide "Fearless Concurrency" through Graph
    // Procedures, any procedure that can reach a blocking operation is marked as blocking so that we can prevent its
    // usage from Graph Functions.
    if (InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.isPresent()) {
      if (((Types.ProcedureType) referencedIdentifierType).getAnnotatedBlocking()) {
        ((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
            .get())
            .resolvedProcedureType.getIsBlocking().set(true);
      }
    }

    // Now that everything checks out, go ahead and mark the function used to satisfy the compiler checks.
    scopedHeap.markIdentifierUsed(this.name);

    return calledFunctionReturnType;
  }

  private static Type getBlockingGenericArgVariantType(
      ScopedHeap scopedHeap, Expr argExpr, Types.ProcedureType maybeBlockingArgType) throws ClaroTypeException {
    Type concreteArgType;
    switch (maybeBlockingArgType.baseType()) {
      case FUNCTION:
        concreteArgType = argExpr.assertSupportedExprType(
            scopedHeap,
            ImmutableSet.of(
                Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
                    maybeBlockingArgType.getArgTypes(),
                    maybeBlockingArgType.getReturnType(),
                    true
                ),
                Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
                    maybeBlockingArgType.getArgTypes(),
                    maybeBlockingArgType.getReturnType(),
                    false
                )
            )
        );
        break;
      case CONSUMER_FUNCTION:
        concreteArgType = argExpr.assertSupportedExprType(
            scopedHeap,
            ImmutableSet.of(
                Types.ProcedureType.ConsumerType.typeLiteralForConsumerArgTypes(
                    maybeBlockingArgType.getArgTypes(), true),
                Types.ProcedureType.ConsumerType.typeLiteralForConsumerArgTypes(
                    maybeBlockingArgType.getArgTypes(), false)
            )
        );
        break;
      case PROVIDER_FUNCTION:
        concreteArgType = argExpr.assertSupportedExprType(
            scopedHeap,
            ImmutableSet.of(
                Types.ProcedureType.ProviderType.typeLiteralForReturnType(
                    maybeBlockingArgType.getReturnType(), true),
                Types.ProcedureType.ProviderType.typeLiteralForReturnType(
                    maybeBlockingArgType.getReturnType(), false)
            )
        );
        break;
      default:
        throw new ClaroParserException("Internal Compiler Error: Grammar allowed a non-procedure type to be annotated blocking!");
    }
    return concreteArgType;
  }

  // TODO(steving) Clean this up; this implementation is the result of factoring out what used to be an inline
  //  implementation above, that reassigned local variables (hence the AtomicReferences).
  public static void validateGenericProcedureCall(
      Optional<Expr> optionalThisExprWithReturnValue, // Only set this for Function/Provider, not Consumer.
      Optional<Type> assertedOutputTypeForGenericFunctionCallUse,
      ImmutableList<Expr> argExprs,
      ScopedHeap scopedHeap,
      // Java is garbage so there's no painless way to do anything resembling multiple returns, so instead here's this
      // gnarly hack. Taking in atomic refs so that I can simply use these as "out params" for lack of a better
      // mechanism w/ language support....
      AtomicReference<Type> calledFunctionReturnType_OUT_PARAM,
      AtomicReference<Types.ProcedureType> referencedIdentifierType_OUT_PARAM,
      AtomicReference<String> procedureName_OUT_PARAM,
      AtomicReference<Optional<ImmutableList<Type>>> optionalConcreteGenericTypeParams_OUT_PARAM) throws ClaroTypeException {
    // We're calling a generic function which means that we need to validate that the generic function's
    // requirements are upheld.
    HashMap<Type, Type> genericTypeParamTypeHashMap = Maps.newHashMap();

    // First, we want to allow "generic return type inference" to influence the expected arg types based on the
    // call-site contextually asserting some or all of the concrete arg types.
    if (assertedOutputTypeForGenericFunctionCallUse.isPresent()) {
      // Here, the programmer is trying to constrain the full concrete type signature of this call contextually.
      Type expectedGenericReturnType = referencedIdentifierType_OUT_PARAM.get().getReturnType();
      try {
        // Make note of the asserted arg types in the generic->concrete type map so that the upcoming arg checking
        // takes these asserted types into account.
        if (assertedOutputTypeForGenericFunctionCallUse.get().baseType().equals(BaseType.ONEOF)
            && !expectedGenericReturnType.baseType().equals(BaseType.ONEOF)) {
          // In this case, we'll need to individually check the asserted variants for any that match. Here we're
          // consciously accepting that some variants obviously *will* fail, so we'll simply catch all errors, and
          // rethrow to get to the error handling below if there was no success case.
          int foundVariantThatMatches = 0;
          for (Type assertedVariantType : ((Types.OneofType) assertedOutputTypeForGenericFunctionCallUse.get()).getVariantTypes()) {
            try {
              StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
                  genericTypeParamTypeHashMap,
                  expectedGenericReturnType,
                  assertedVariantType
              );
              ++foundVariantThatMatches;
            } catch (ClaroTypeException ignored) {
              // Do nothing, just try other variants.
            }
          }
          if (foundVariantThatMatches != 1) { // If we match more than 1 it's a big problem.
            // Hey, to be fair, maybe goto-stmts would be useful sometimes....cuz hey, I'm more than capable of writing
            // spaghetti code even w/o the help of goto. Just jumping down to the error handling logic.
            throw new ClaroTypeException("Internal Compiler Error! Found " + foundVariantThatMatches +
                                         " matches, expected 1! (You shouldn't be seeing this, this should be IGNORED).");
          }
        } else {
          StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
              genericTypeParamTypeHashMap,
              expectedGenericReturnType,
              assertedOutputTypeForGenericFunctionCallUse.get()
          );
        }
      } catch (ClaroTypeException ignored) {
        // In this case, we know that we can let the type error be thrown by the Expr.assertExpectedExprType.
        Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages =
            Optional.of(genericTypeParamTypeHashMap);
        calledFunctionReturnType_OUT_PARAM.set(expectedGenericReturnType);
        // There's no point in checking any of the args if there's no way that the call will yield the correct type.
        return;
      }
      calledFunctionReturnType_OUT_PARAM.set(assertedOutputTypeForGenericFunctionCallUse.get());
    }

    // Then, we'll check that the args match the ordering pattern of the generic signature.
    boolean foundBlockingFuncArg = false;
    for (int i = 0; i < argExprs.size(); i++) {
      int existingTypeErrorsFoundCount = Expr.typeErrorsFound.size();
      Type argType = referencedIdentifierType_OUT_PARAM.get().getArgTypes().get(i);
      HashMap<Type, Type> mapOfGenTypeRefsInCurrArg = Maps.newHashMap();
      Type argInferredTypeWithNoTypeParamRefs =
          StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
              mapOfGenTypeRefsInCurrArg, argType, argType);
      try {
        // This is less efficient, but let's try to improve ergonomics of generic function calls by *ASSERTING* types
        // on args where either it makes no reference to a generic type param, or *ALL* referenced generic type params
        // were already explicitly provided by the contextual type assertion.
        Type validatedType;
        boolean representsConstructor = procedureName_OUT_PARAM.get().contains("$constructor");
        if (representsConstructor && mapOfGenTypeRefsInCurrArg.isEmpty()) {
          // It turns out there were actually no references to the generic type params whatsoever, so we can go straight
          // to type assertion on this arg.
          argExprs.get(i).assertExpectedExprType(scopedHeap, argInferredTypeWithNoTypeParamRefs);
          validatedType = argInferredTypeWithNoTypeParamRefs;
        } else if (representsConstructor &&
                   genericTypeParamTypeHashMap.keySet().containsAll(mapOfGenTypeRefsInCurrArg.keySet())) {
          // We now know that in fact, we could do direct type assertion on this arg once we fill in the concrete arg
          // based on the concrete types we inferred from the contextual return type assertion.
          Type argTypeInferredFromConcreteTypeParamsInContextualReturnTypeAssertion =
              StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
                  genericTypeParamTypeHashMap, argType, argType, /*inferConcreteTypes=*/true);
          argExprs.get(i).assertExpectedExprType(
              scopedHeap, argTypeInferredFromConcreteTypeParamsInContextualReturnTypeAssertion);
          validatedType = argTypeInferredFromConcreteTypeParamsInContextualReturnTypeAssertion;
        } else {
          validatedType =
              StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
                  genericTypeParamTypeHashMap, argType, argExprs.get(i).getValidatedExprType(scopedHeap));
        }
        if (validatedType instanceof Types.ProcedureType) {
          // Since the above structural type checking doesn't actually validate the complete signature, check that
          // explicitly here to pick up any blocking annotation(s).
          if (!Objects.equals(((Types.ProcedureType) validatedType).getAnnotatedBlocking(), ((Types.ProcedureType) argType).getAnnotatedBlocking())) {
            // Hack to get things to fall into below catch-clause to get a clean error message.
            throw new ClaroTypeException("Internal Compiler Error: Blocking Annotation Mismatch Between Args!");
          }
          foundBlockingFuncArg |= ((Types.ProcedureType) validatedType).getIsBlocking().get();
        }
      } catch (ClaroTypeException inferenceError) {

        ////////////////////////////////////////////////////////////////////////////////
        // BEGIN WARNING: "THEN_CHANGE(UNDECIDED)"
        ////////////////////////////////////////////////////////////////////////////////
        if (inferenceError.getMessage().contains("UNDECIDED")) {
          argExprs.get(i).logTypeError(inferenceError);
          continue;
        }
        ////////////////////////////////////////////////////////////////////////////////
        // END WARNING: "THEN_CHANGE(UNDECIDED)"
        ////////////////////////////////////////////////////////////////////////////////

        // In case we're not able to structurally validate this arg's type aligns with the generic structure of the
        // function's expected arg type, then we want to alert the programmer with a log line that actually points to
        // the entire arg, not the one place where there was a mismatch internally in the structure. This is a UX choice
        // that I might come to have a different opinion on later.

        // We want to only provide the more specific error message that is actually actionable, drop noise.
        Expr.typeErrorsFound.setSize(existingTypeErrorsFoundCount);

        // Infer whatever concrete type info that we can from the concrete types that we've already come across.
        Type inferredRequiredConcreteType =
            StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
                // Must use a copy of the inferred type map because otherwise generic types will be added to the map
                // accidentally as though they are concrete types - this would invalidate the type checks on
                // subsequent args.
                (HashMap<Type, Type>) genericTypeParamTypeHashMap.clone(),
                argType,
                argType,
                /*inferConcreteTypes=*/ true
            );

        // If we're trying to do a type assertion of a first-class procedure arg without having fully inferred the
        // Generic types that compose the procedure arg, we need to throw a specific error message indicating that
        // the caller must assert the type of the procedure arg themselves with either a cast or the inline-typed
        // lambda syntax (i.e. `(x:int) -> boolean { ... }`).
        if (argType instanceof Types.ProcedureType
            && inferenceError.getMessage().contains("Ambiguous Lambda Expression Type:")
            && (((Types.ProcedureType) inferredRequiredConcreteType).getArgTypes().stream()
                    .anyMatch(t -> t instanceof Types.$GenericTypeParam)
                || (((Types.ProcedureType) inferredRequiredConcreteType).hasReturnValue()
                    &&
                    ((Types.ProcedureType) inferredRequiredConcreteType).getReturnType() instanceof Types.$GenericTypeParam))) {

          Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages =
              Optional.of(genericTypeParamTypeHashMap);
          String partialInferredLambdaType = inferredRequiredConcreteType.toString();
          Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages = Optional.empty();
          argExprs.get(i)
              .logTypeError(
                  ClaroTypeException.forAmbiguousLambdaFirstClassGenericArg(i, partialInferredLambdaType));
        } else {
          if (inferredRequiredConcreteType instanceof Types.ProcedureType
              && ((Types.ProcedureType) inferredRequiredConcreteType).getAnnotatedBlocking() == null) {
            // Here we validate that the actual given arg is the correct type modulo the blocking annotation.
            Type concreteType =
                FunctionCallExpr.getBlockingGenericArgVariantType(
                    scopedHeap, argExprs.get(i), (Types.ProcedureType) inferredRequiredConcreteType);
            foundBlockingFuncArg |= ((Types.ProcedureType) concreteType).getAnnotatedBlocking();
          } else {
            if (inferredRequiredConcreteType.baseType().equals(BaseType.ONEOF)) {
              // Since the arg type itself is a oneof, then we actually really need to accept any variant type.
              argExprs.get(i).assertSupportedExprOneofTypeVariant(
                  scopedHeap,
                  inferredRequiredConcreteType,
                  ((Types.OneofType) inferredRequiredConcreteType).getVariantTypes()
              );
            } else {
              argExprs.get(i).assertExpectedExprType(scopedHeap, inferredRequiredConcreteType);
            }
          }
        }
      }
    }
    // Now, we need to check if the output type should have been constrained by the arg types, or by the
    // surrounding context. Only do this checking if the procedure call produces an output.
    if (!assertedOutputTypeForGenericFunctionCallUse.isPresent() && optionalThisExprWithReturnValue.isPresent()) {
      // Here, the programmer assumes that the args to this procedure alone were sufficient to constrain the full
      // concrete type signature.
      for (String genericTypeParamName : referencedIdentifierType_OUT_PARAM.get().getGenericProcedureArgNames().get()) {
        // We need to validate that all of the generic param names have had concrete types assigned by the arg checks alone.
        if (!genericTypeParamTypeHashMap.containsKey(Types.$GenericTypeParam.forTypeParamName(genericTypeParamName))) {
          // at least one of the generic type parameters is not fully understood by the args alone, so we need to
          // let the programmer know that they must constrain that contextually.
          Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages =
              Optional.of(genericTypeParamTypeHashMap);
          ClaroTypeException e =
              ClaroTypeException.forGenericProcedureCallWithoutOutputTypeSufficientlyConstrainedByArgsAndContext(procedureName_OUT_PARAM.get(), referencedIdentifierType_OUT_PARAM.get());
          Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages = Optional.empty();
          optionalThisExprWithReturnValue.get().logTypeError(e);
          return;
        }
      }
      // Here, I'm confident that I should be able to do inference of the actual output type.
      // This probably seems crazy, passing the same type twice, but the point is that with inferTypes set to true
      // if the generic type is found within the concrete types map, that concrete type will be returned instead of
      // the generic type in the "actual" type we pass in.
      calledFunctionReturnType_OUT_PARAM.set(
          StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
              genericTypeParamTypeHashMap,
              referencedIdentifierType_OUT_PARAM.get().getReturnType(),
              referencedIdentifierType_OUT_PARAM.get().getReturnType(),
              /*inferConcreteTypes=*/true
          ));
    }
    // Finally, need to ensure that the required contracts are supported by the requested concrete types
    // otherwise this would be an invalid call to the generic procedure.
    if (!InternalStaticStateUtil.GnericProcedureDefinitionStmt_withinGenericProcedureDefinitionTypeValidation) {
      ArrayListMultimap<String, ImmutableList<Type>> genericFunctionRequiredContractsMap =
          referencedIdentifierType_OUT_PARAM.get().getAllTransitivelyRequiredContractNamesToGenericArgs();
      for (String requiredContract : genericFunctionRequiredContractsMap.keySet()) {
        for (ImmutableList<Type> requiredContractTypeParamNames :
            genericFunctionRequiredContractsMap.get(requiredContract)) {
          ImmutableList.Builder<Type> requiredContractConcreteTypesBuilder = ImmutableList.builder();
          ImmutableList.Builder<String> requiredContractConcreteTypeStringsBuilder = ImmutableList.builder();
          for (Type requiredContractTypeParam : requiredContractTypeParamNames) {
            Type concreteType = genericTypeParamTypeHashMap.get(requiredContractTypeParam);
            requiredContractConcreteTypesBuilder.add(concreteType);
            requiredContractConcreteTypeStringsBuilder.add(concreteType.toString());
          }
          ImmutableList<String> requiredContractConcreteTypeStrings =
              requiredContractConcreteTypeStringsBuilder.build();
          if (!scopedHeap.isIdentifierDeclared(
              ContractImplementationStmt.getContractTypeString(requiredContract, requiredContractConcreteTypeStrings))) {
            List<String> requiredContractImplsForDynamicDispatchSupport =
                ContractFunctionCallExpr.getAllDynamicDispatchConcreteContractProcedureNames(
                    requiredContract,
                    // Filter the set of supported dispatch args, based on the actual args having tried to nest a oneof.
                    IntStream.range(0, requiredContractTypeParamNames.size())
                        .boxed()
                        .collect(ImmutableList.toImmutableList()),
                    requiredContractConcreteTypesBuilder.build()
                );
            // If it turns out that dynamic dispatch would be possible, then the call to this generic function should be
            // allowed so that the function body can just go ahead and codegen dynamic dispatch calls.
            boolean dynamicDispatchIsSupportedOverCurrentContract =
                !requiredContractImplsForDynamicDispatchSupport.isEmpty()
                && requiredContractImplsForDynamicDispatchSupport.stream().allMatch(scopedHeap::isIdentifierDeclared);
            if (!dynamicDispatchIsSupportedOverCurrentContract) {
              throw ClaroTypeException.forGenericProcedureCallForConcreteTypesWithRequiredContractImplementationMissing(
                  procedureName_OUT_PARAM.get(), referencedIdentifierType_OUT_PARAM.get(), requiredContract, requiredContractConcreteTypeStrings);
            }
          }
        }
      }
    }

    // Don't mark the called generic for monomorphization if this is a call to a generic function during
    // generic (non-monomorphization) type checking. This call would not be representative of something we
    // actually want to monomorphize.
    if (InternalStaticStateUtil.GnericProcedureDefinitionStmt_withinGenericProcedureDefinitionTypeValidation) {
      // Only doing Generic Contract Requirement propagation during the generic type validation, not for each
      // individual monomorphization.
      ((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
          .get())
          .directTopLevelGenericProcedureDepsCalledGenericTypesMappings
          .put(procedureName_OUT_PARAM.get(), ImmutableMap.copyOf(genericTypeParamTypeHashMap));
    } else {
      // I want to mark this concrete signature for Monomorphization codegen! Note - this is subtle, but the
      // single monomorphization will be reused by both the blocking and non-blocking variants if the generic
      // procedure happened to also be blocking-generic as this subtle difference has no impact on the gen'd code..
      if (!procedureName_OUT_PARAM.get().contains("$constructor")
          && !procedureName_OUT_PARAM.get().contains("$$MONOMORPHIZATION")) {
        // Ignore all Generic Type default Constructors, b/c these are synthetic functions that don't actually
        // produce any necessary codegen.
        procedureName_OUT_PARAM.set(
            ((BiFunction<ScopedHeap, ImmutableMap<Type, Type>, String>)
                 scopedHeap.getIdentifierValue(procedureName_OUT_PARAM.get())).apply(scopedHeap, ImmutableMap.copyOf(genericTypeParamTypeHashMap)));
      }
      optionalConcreteGenericTypeParams_OUT_PARAM.set(
          Optional.of(
              referencedIdentifierType_OUT_PARAM.get().getGenericProcedureArgNames().get()
                  .stream()
                  .map(n -> genericTypeParamTypeHashMap.get(Types.$GenericTypeParam.forTypeParamName(n)))
                  .collect(ImmutableList.toImmutableList())));
    }

    // If this was blocking-generic in addition, then we need to update the referenced identifier type.
    if (referencedIdentifierType_OUT_PARAM.get().getAnnotatedBlocking() == null) {
      // From now, we'll stop validating against the generic type signature, and validate against this concrete
      // signature since we know which one we want to use to continue type-checking with now. (Note, this is a
      // bit of a white lie - the blocking concrete variant signature has *all* blocking for the blocking-generic
      // args, which is not necessarily the case for the *actual* args, but this doesn't matter since after this
      // point we're actually done with the args type checking, and we'll move onto codegen where importantly the
      // generated code is all exactly the same across blocking/non-blocking).
      referencedIdentifierType_OUT_PARAM.set(
          (Types.ProcedureType) scopedHeap.getValidatedIdentifierType(
              (foundBlockingFuncArg ? "$blockingConcreteVariant_" : "$nonBlockingConcreteVariant_")
              // Refer to the name of the procedure as defined by the owning ProcedureDefinitionStmt or GenericFunctionDef
              + referencedIdentifierType_OUT_PARAM.get().getGenericProcedureArgNames()
                  .map(unused -> { // Now know that we're looking at a GenericProcedureDefinitionStmt.
                    try {
                      // Make use of reflection because I'm sick of fighting Bazel's circular deps restrictions. It's
                      // already made a major mess of this code just trying not to reference the type
                      // GenericFunctionDefinitionStmt by name...
                      return referencedIdentifierType_OUT_PARAM.get().getProcedureDefStmt().getClass()
                          .getField("functionName")
                          .get(referencedIdentifierType_OUT_PARAM.get().getProcedureDefStmt());
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                      throw new ClaroParserException("Internal Compiler Error: GenericFunctionDefinitionStmt.functionName not found. Some code change broke the hacky reflection looking up the field.");
                    }
                  })
                  .orElseGet(() ->
                                 ((ProcedureDefinitionStmt) referencedIdentifierType_OUT_PARAM.get()
                                     .getProcedureDefStmt())
                                     .procedureName)));
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Determine right away if this is going to be a static procedure call (meaning no indirection via a first-class
    // procedure reference.
    boolean isStatic = scopedHeap.getIdentifierData(this.name).isStaticValue;

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
    if (!this.name.contains("$MONOMORPHIZATION")) {
      scopedHeap.markIdentifierUsed(this.name);
    } else {
      this.hashNameForCodegen = true;
      if (this.optionalOriginatingDepModuleName.isPresent() ||
          InternalStaticStateUtil.DEP_MODULE_MONOMORPHIZATION_ENABLED) {
        // Additionally, b/c this is a call to a monomorphization, if the procedure is actually located in a dep module,
        // that means that the monomorphization can't be guaranteed to *actually* have been generated in the dep
        // module's codegen. This is b/c all Claro Modules are compiled in isolation - meaning they don't know how
        // they'll be used by any potential future callers. This has the unfortunate side-effect on generic procedures
        // exported by Modules needing to be codegen'd by the callers rather than at the definition. So, here we'll go
        // ahead and drop the namespacing dep$module.foo(...) -> this$module$dep$module$MONOMORPHIZATIONS.foo(...) so that we can reference the local codegen.
        String originatingModuleDisambig =
            ScopedHeap.getDefiningModuleDisambiguator(this.optionalOriginatingDepModuleName);
        optionalNormalizedOriginatingDepModulePrefix =
            Optional.of(
                String.format(
                    "$MONOMORPHIZATION$%s$%s$%s.",
                    originatingModuleDisambig.substring(originatingModuleDisambig.lastIndexOf('$') + 1),
                    Hashing.sha256().hashUnencodedChars(originatingModuleDisambig),
                    (hashedName = Optional.of(getHashedName())).get()
                ));
      }
    }

    if (this.hashNameForCodegen) {
      // In order to call the actual monomorphization, we need to ensure that the name isn't too long for Java.
      // So, we're following a hack where all monomorphization names are sha256 hashed to keep them short while
      // still unique.
      this.name = hashedName.orElseGet(this::getHashedName);
    }

    AtomicReference<GeneratedJavaSource> exprsGenJavaSource =
        new AtomicReference<>(GeneratedJavaSource.forJavaSourceBody(new StringBuilder()));
    String exprsJavaSourceBodyCodegen =
        this.argExprs
            .stream()
            .map(expr -> {
              GeneratedJavaSource currGenJavaSource = expr.generateJavaSourceOutput(scopedHeap);
              String currJavaSourceBody = currGenJavaSource.javaSourceBody().toString();
              // We've already consumed the javaSourceBody, it's safe to clear it.
              currGenJavaSource.javaSourceBody().setLength(0);
              exprsGenJavaSource.set(exprsGenJavaSource.get().createMerged(currGenJavaSource));
              return currJavaSourceBody;
            })
            .collect(Collectors.joining(", "));
    GeneratedJavaSource functionCallJavaSourceBody;
    if (this.representsUserDefinedTypeConstructor.isPresent()) {
      // Depending on whether this type def was parsed from a dep module, this may have been named with a
      // disambiguating prefix like "$DEP_MODULE$module$". For the sake of codegen'd values containing consistent
      // names everywhere, I need to strip any prefixing here so that instances of this type created ANYWHERE will
      // definitely evaluate as having the same type.
      String canonicalizedTypeName = this.originalName.substring(this.originalName.lastIndexOf("$") + 1);
      functionCallJavaSourceBody = GeneratedJavaSource.forJavaSourceBody(
          new StringBuilder(
              String.format(
                  "new $UserDefinedType(\"%s\", /*definingModuleDisambiguator=*/\"%s\", /*parameterizedTypes=*/%s, /*wrappedType=*/%s, /*wrappedValue=*/%s)",
                  canonicalizedTypeName,
                  ScopedHeap.getDefiningModuleDisambiguator(this.optionalOriginatingDepModuleName),
                  this.optionalConcreteGenericTypeParams.orElse(ImmutableList.of()).stream()
                      .map(Type::getJavaSourceClaroType)
                      .collect(Collectors.joining(", ", "ImmutableList.of(", ")")),
                  this.representsUserDefinedTypeConstructor.get().getJavaSourceClaroType(),
                  exprsJavaSourceBodyCodegen
              )
          )
      );
    } else {
      if (isStatic) {
        String procName =
            this.optionalOriginatingDepModuleName
                .map(depMod -> this.name.replace(String.format("$DEP_MODULE$%s$", depMod), ""))
                .orElse(this.name);
        functionCallJavaSourceBody = GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder(String.format(
                "%s$%s.%s(%s)",
                optionalNormalizedOriginatingDepModulePrefix.orElse(""),
                procName,
                procName,
                exprsJavaSourceBodyCodegen
            )));
      } else {
        functionCallJavaSourceBody = GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder(
                String.format(
                    this.staticDispatchCodegen ? "%s%s(%s%s)" : "%s%s.apply(%s%s)",
                    optionalNormalizedOriginatingDepModulePrefix.orElse(""),
                    this.optionalOriginatingDepModuleName
                        .map(depMod -> this.name.replace(String.format("$DEP_MODULE$%s$", depMod), ""))
                        .orElse(this.name),
                    exprsJavaSourceBodyCodegen,
                    this.staticDispatchCodegen && this.optionalExtraArgsCodegen.isPresent()
                    ? ", " + this.optionalExtraArgsCodegen.get()
                    : ""
                )
            )
        );
      }
    }

    // This node will be potentially reused assuming that it is called within a Generic function that gets
    // monomorphized as that process will reuse the exact same nodes over multiple sets of types. So reset
    // the name now.
    this.name = this.originalName;
    this.optionalConcreteGenericTypeParams = Optional.empty();
    this.assertedOutputTypeForGenericFunctionCallUse = null;

    // We definitely don't want to be throwing away the static definitions and preambles required for the exprs
    // passed as args to this function call, so ensure that they're correctly collected and passed on here.
    return functionCallJavaSourceBody.createMerged(exprsGenJavaSource.get());
  }

  private String getHashedName() {
    return String.format(
        "%s__%s",
        this.optionalOriginatingDepModuleName
            .map(depMod -> this.originalName.replace(String.format("$DEP_MODULE$%s$", depMod), ""))
            .orElse(this.originalName),
        Hashing.sha256().hashUnencodedChars(this.name).toString()
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((Types.ProcedureType.ProcedureWrapper) scopedHeap.getIdentifierValue(this.name))
        .apply(this.argExprs, scopedHeap);
  }

  public static void validateNeededBindings(String functionName, Type referencedIdentifierType, ScopedHeap scopedHeap)
      throws ClaroTypeException {
    // Validate that the procedure has been called in a scope that provides the correct bindings.
    // We only care about referencing top-level functions, not any old function (e.g. not lambdas or func refs).
    if (scopedHeap.findIdentifierInitializedScopeLevel(functionName).orElse(-1) == 0) {
      if (InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.isPresent()) {
        // Make sure that if this function call is within a ProcedureDefStmt then we actually need to make sure
        // to register function call with that active procedure def stmt instance so that it knows which top-level
        // procedures it needs to check with to accumulate its transitive used injected keys set.
        if (!UsingBlockStmt.currentlyUsedBindings.isEmpty()) {
          // Using-blocks are actually supported from directly within a procedure definition, so if this procedure call
          // is from within a using block, we'd need to filter the set that we add to the ProcedureDefStmt's direct deps.
          ((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
              .get())
              .directTopLevelProcedureDepsToBeFilteredForExplicitUsingBlockKeyBindings
              .merge(
                  functionName,
                  ImmutableSet.copyOf(UsingBlockStmt.currentlyUsedBindings),
                  // It's possible that this same procedure was already used in a different using block so we just need
                  // the union of the currently used keys to get the minimum.
                  (existingUsedBindingsSet, currUsedBindingsSet) ->
                      Sets.union(existingUsedBindingsSet, currUsedBindingsSet)
                          .stream()
                          .collect(ImmutableSet.toImmutableSet())
              );
        } else {
          ((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
              .get())
              .directTopLevelProcedureDepsSet.add(functionName);
        }
      } else {
        // Calls that are not in a ProcedureDefStmt simply need to be validated.
        Set<Key> currNeededBindings =
            ((Types.ProcedureType) scopedHeap.getValidatedIdentifierType(functionName)).getUsedInjectedKeys();
        if (!UsingBlockStmt.currentlyUsedBindings.containsAll(currNeededBindings)) {
          throw ClaroTypeException.forMissingBindings(
              functionName, referencedIdentifierType, Sets.difference(currNeededBindings, UsingBlockStmt.currentlyUsedBindings));
        }
      }
    }
  }
}
