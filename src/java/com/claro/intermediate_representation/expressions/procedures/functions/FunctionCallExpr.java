package com.claro.intermediate_representation.expressions.procedures.functions;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.ProcedureDefinitionStmt;
import com.claro.intermediate_representation.statements.UsingBlockStmt;
import com.claro.intermediate_representation.statements.contracts.ContractImplementationStmt;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.google.common.hash.Hashing;

import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FunctionCallExpr extends Expr {
  public String name;
  public boolean hashNameForCodegen = false;
  public final ImmutableList<Expr> argExprs;
  private Type assertedOutputTypeForGenericFunctionCallUse;
  private String originalName;

  public FunctionCallExpr(String name, ImmutableList<Expr> args, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.name = name;
    this.originalName = name;
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
    Type referencedIdentifierType = scopedHeap.getValidatedIdentifierType(this.name);
    Preconditions.checkState(
        // Include CONSUMER_FUNCTION just so that later we can throw a more specific error for that case.
        ImmutableSet.of(BaseType.FUNCTION, BaseType.PROVIDER_FUNCTION, BaseType.CONSUMER_FUNCTION)
            .contains(referencedIdentifierType.baseType()),
        "Non-function %s %s cannot be called!",
        referencedIdentifierType,
        this.name
    );
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

    if (((Types.ProcedureType.FunctionType) referencedIdentifierType).getAnnotatedBlocking() == null) {
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
          Type concreteArgType;
          switch (maybeBlockingArgType.baseType()) {
            case FUNCTION:
              concreteArgType = argExprs.get(i).assertSupportedExprType(
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
              concreteArgType = argExprs.get(i).assertSupportedExprType(
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
              concreteArgType = argExprs.get(i).assertSupportedExprType(
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
            argExprs.get(i).assertExpectedExprType(scopedHeap, definedArgTypes.get(i));
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
      GenericProcedureCallChecks:
      {
        // We're calling a generic function which means that we need to validate that the generic function's
        // requirements are upheld.
        // First, we'll check that the args match the ordering pattern of the generic signature.
        HashMap<Types.$GenericTypeParam, Type> genericTypeParamTypeHashMap = Maps.newHashMap();
        boolean successfullyValidatedArgs = true;
        for (int i = 0; i < argExprs.size(); i++) {
          int existingTypeErrorsFoundCount = Expr.typeErrorsFound.size();
          Type argType = ((Types.ProcedureType) referencedIdentifierType).getArgTypes().get(i);
          try {
            validateArgExprsAndExtractConcreteGenericTypeParams(
                genericTypeParamTypeHashMap, argType, argExprs.get(i).getValidatedExprType(scopedHeap));
          } catch (ClaroTypeException ignored) {
            // In case we're not able to structurally validate this arg's type aligns with the generic structure of the
            // function's expected arg type, then we want to alert the programmer with a log line that actually points to
            // the entire arg, not the one place where there was a mismatch internally in the structure. This is a UX choice
            // that I might come to have a different opinion on later.
            Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages =
                Optional.of(genericTypeParamTypeHashMap);
            argExprs.get(i)
                .assertExpectedExprType(scopedHeap, genericTypeParamTypeHashMap.getOrDefault(argType, argType));
            Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages = Optional.empty();

            // Before declaring a failure on validating types, first validate that we actually logged a type validation
            // error. In the case that the arg that we're currently validating is actually a concrete type and not a
            // generic type, then it's possible that there was originally an error when trying to query the type from
            // the argExpr, but not when we asserted the type on it directly. This would specifically be the case for
            // uncasted lambdas and tuple-subscripts.
            if (existingTypeErrorsFoundCount < Expr.typeErrorsFound.size()) {
              if (ignored.getMessage().contains("Ambiguous Lambda Expression Type:")) {
                // We want to only provide the more specific error message that is actually actionable, drop noise.
                Expr.typeErrorsFound.setSize(existingTypeErrorsFoundCount);
                argExprs.get(i).logTypeError(ignored);
              }

              successfullyValidatedArgs = false;
            }
          }
        }
        if (!successfullyValidatedArgs) {
          // I've literally never used this Java feature before and it's kinda blowing my mind, but this is preventing
          // us from completely uselessly checking the return types since we don't even have a match the generic types
          // needed for the args, so there's no way to use that information to derive what the correct return type should be.
          break GenericProcedureCallChecks;
        }
        // Now, we need to check if the output type should have been constrained by the arg types, or by the
        // surrounding context.
        if (this.assertedOutputTypeForGenericFunctionCallUse == null) {
          // Here, the programmer assumes that the args to this procedure alone were sufficient to constrain the full
          // concrete type signature.
          for (String genericTypeParamName : ((Types.ProcedureType) referencedIdentifierType).getGenericProcedureArgNames()
              .get()) {
            // We need to validate that all of the generic param names have had concrete types assigned by the arg checks alone.
            if (!genericTypeParamTypeHashMap.containsKey(Types.$GenericTypeParam.forTypeParamName(genericTypeParamName))) {
              // at least one of the generic type parameters is not fully understood by the args alone, so we need to
              // let the programmer know that they must constrain that contextually.
              Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages =
                  Optional.of(genericTypeParamTypeHashMap);
              ClaroTypeException e =
                  ClaroTypeException.forGenericProcedureCallWithoutOutputTypeSufficientlyConstrainedByArgsAndContext(this.name, referencedIdentifierType);
              Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages = Optional.empty();
              this.logTypeError(e);
              break GenericProcedureCallChecks;
            }
          }
          // Here, I'm confident that I should be able to do inference of the actual output type.
          // This probably seems crazy, passing the same type twice, but the point is that with inferTypes set to true
          // if the generic type is found within the concrete types map, that concrete type will be returned instead of
          // the generic type in the "actual" type we pass in.
          calledFunctionReturnType = validateArgExprsAndExtractConcreteGenericTypeParams(
              genericTypeParamTypeHashMap,
              ((Types.ProcedureType) referencedIdentifierType).getReturnType(),
              ((Types.ProcedureType) referencedIdentifierType).getReturnType(),
              /*inferConcreteTypes=*/true
          );
        } else {
          // Here, the programmer is trying to constrain the full concrete type signature of this call contextually.
          Type expectedGenericReturnType = ((Types.ProcedureType) referencedIdentifierType).getReturnType();
          try {
            validateArgExprsAndExtractConcreteGenericTypeParams(
                genericTypeParamTypeHashMap,
                expectedGenericReturnType,
                this.assertedOutputTypeForGenericFunctionCallUse
            );
          } catch (ClaroTypeException ignored) {
            // In this case, we know that we can let the type error be thrown by the Expr.assertExpectedExprType.
            Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages =
                Optional.of(genericTypeParamTypeHashMap);
            calledFunctionReturnType = expectedGenericReturnType;
            break GenericProcedureCallChecks;
          }
          calledFunctionReturnType = this.assertedOutputTypeForGenericFunctionCallUse;
        }
        // Finally, need to ensure that the required contracts are supported by the requested concrete types
        // otherwise this would be an invalid call to the generic procedure.
        if (!InternalStaticStateUtil.GnericProcedureDefinitionStmt_withinGenericProcedureDefinitionTypeValidation
            && ((Types.ProcedureType.FunctionType) referencedIdentifierType)
                .getOptionalRequiredContractNamesToGenericArgs().isPresent()) {
          ImmutableMap<String, ImmutableList<Types.$GenericTypeParam>> genericFunctionRequiredContractsMap =
              ((Types.ProcedureType) referencedIdentifierType).getOptionalRequiredContractNamesToGenericArgs().get();
          for (String requiredContract : genericFunctionRequiredContractsMap.keySet()) {
            ImmutableList.Builder<String> requiredContractConcreteTypesBuilder = ImmutableList.builder();
            ImmutableList<Types.$GenericTypeParam> requiredContractTypeParamNames =
                ((Types.ProcedureType) referencedIdentifierType).getOptionalRequiredContractNamesToGenericArgs()
                    .get()
                    .get(requiredContract);
            for (Types.$GenericTypeParam requiredContractTypeParam : requiredContractTypeParamNames) {
              requiredContractConcreteTypesBuilder.add(
                  genericTypeParamTypeHashMap.get(requiredContractTypeParam).toString());
            }
            ImmutableList<String> requiredContractConcreteTypes = requiredContractConcreteTypesBuilder.build();
            if (!scopedHeap.isIdentifierDeclared(ContractImplementationStmt.getContractTypeString(
                requiredContract, requiredContractConcreteTypes))) {
              throw ClaroTypeException.forGenericProcedureCallForConcreteTypesWithRequiredContractImplementationMissing(
                  this.name, referencedIdentifierType, requiredContract, requiredContractConcreteTypes);
            }
          }
        }

        // We actually will need to skip this portion if this is a recursive call to a generic function during
        // the generic type checking of that function. This recursive call is not representative of something we
        // actually want to monomorphize.
        if (!InternalStaticStateUtil.GnericProcedureDefinitionStmt_withinGenericProcedureDefinitionTypeValidation) {
          // I want to mark this concrete signature for Monomorphization codegen!
          this.name =
              ((BiFunction<ScopedHeap, ImmutableMap<Types.$GenericTypeParam, Type>, String>)
                   scopedHeap.getIdentifierValue(this.name)).apply(scopedHeap, ImmutableMap.copyOf(genericTypeParamTypeHashMap));
        }
      } // END LABEL GenericProcedureCallChecks:
    } else {
      // Validate that all of the given parameter Exprs are of the correct type.
      for (int i = 0; i < this.argExprs.size(); i++) {
        Expr currArgExpr = this.argExprs.get(i);
        // We have to handle the case where the defined arg type is actually a generic param, in which case
        // we're actually currently validating against a generic function definition body and only care that
        // the type we're passing in is also a generic type (since the correct type will be validated at the
        // call site).
        if (false && definedArgTypes.get(i).baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
          argExprs.get(i).assertExpectedBaseType(scopedHeap, BaseType.$GENERIC_TYPE_PARAM);
        } else {
          // This arg is not being treated as generic, so assert against the static defined type.
          currArgExpr.assertExpectedExprType(scopedHeap, definedArgTypes.get(i));
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

  private static Type validateArgExprsAndExtractConcreteGenericTypeParams(
      HashMap<Types.$GenericTypeParam, Type> genericTypeParamTypeHashMap,
      Type functionExpectedArgType,
      Type actualArgExprType) throws ClaroTypeException {
    return validateArgExprsAndExtractConcreteGenericTypeParams(
        genericTypeParamTypeHashMap, functionExpectedArgType, actualArgExprType, false);
  }

  private static Type validateArgExprsAndExtractConcreteGenericTypeParams(
      HashMap<Types.$GenericTypeParam, Type> genericTypeParamTypeHashMap,
      Type functionExpectedArgType,
      Type actualArgExprType,
      boolean inferConcreteTypes) throws ClaroTypeException {
    ClaroTypeException DEFAULT_TYPE_MISMATCH_EXCEPTION =
        new ClaroTypeException("Couldn't construct matching concrete type");
    // These nested types are types that we'll structurally recurse into to search for concrete usages
    // of the generic type params.
    Type validatedReturnType = null;
    final ImmutableSet<BaseType> nestedBaseTypes =
        ImmutableSet.of(BaseType.FUNCTION, BaseType.CONSUMER_FUNCTION, BaseType.FUTURE, BaseType.TUPLE, BaseType.LIST);
    if (functionExpectedArgType.baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
      // In the case that this positional arg is a generic param type, then actually we need to just accept
      // whatever type is in the passed arg expr.
      if (genericTypeParamTypeHashMap.containsKey(functionExpectedArgType)) {
        // The type of this particular generic param has already been determined by an earlier arg over the
        // same generic type param, so actually this arg expr MUST have the same type.
        if (inferConcreteTypes) {
          return genericTypeParamTypeHashMap.get(functionExpectedArgType);
        } else if (actualArgExprType.equals(genericTypeParamTypeHashMap.get(functionExpectedArgType))) {
          return actualArgExprType;
        } else {
          throw DEFAULT_TYPE_MISMATCH_EXCEPTION;
        }
      } else {
        genericTypeParamTypeHashMap.put((Types.$GenericTypeParam) functionExpectedArgType, actualArgExprType);
        return actualArgExprType;
      }
    } else if (nestedBaseTypes.contains(functionExpectedArgType.baseType())) {
      // We're going to need to do structural type validation to derive the expected generic type.

      // First I just need to validate that the base types are actually even matching.
      if (!actualArgExprType.baseType().equals(functionExpectedArgType.baseType())) {
        throw DEFAULT_TYPE_MISMATCH_EXCEPTION;
      }

      // Now recurse into the structure to check internal types.
      switch (functionExpectedArgType.baseType()) {
        case FUNCTION:
        case PROVIDER_FUNCTION:
          validatedReturnType =
              validateArgExprsAndExtractConcreteGenericTypeParams(
                  genericTypeParamTypeHashMap,
                  ((Types.ProcedureType) functionExpectedArgType).getReturnType(),
                  ((Types.ProcedureType) actualArgExprType).getReturnType(),
                  inferConcreteTypes
              );
          if (functionExpectedArgType.baseType().equals(BaseType.PROVIDER_FUNCTION)) {
            // Providers have no args so don't fall into the next checks.
            return Types.ProcedureType.ProviderType.typeLiteralForReturnType(
                validatedReturnType, ((Types.ProcedureType) functionExpectedArgType).getAnnotatedBlocking());
          }
          // Intentional fallthrough - cuz hey programming should be fun and cute sometimes. Fight me, future Jason.
        case CONSUMER_FUNCTION: // Both FUNCTION and CONSUMER_FUNCTION need to validate args, so do it once here.
          ImmutableList<Type> expectedArgTypes = ((Types.ProcedureType) functionExpectedArgType).getArgTypes();
          ImmutableList<Type> actualArgTypes = ((Types.ProcedureType) actualArgExprType).getArgTypes();
          // First check that we have the matching number of args.
          if (actualArgTypes.size() != expectedArgTypes.size()) {
            throw DEFAULT_TYPE_MISMATCH_EXCEPTION;
          }
          ImmutableList.Builder<Type> validatedArgTypes = ImmutableList.builder();
          for (int i = 0; i < expectedArgTypes.size(); i++) {
            validatedArgTypes.add(
                validateArgExprsAndExtractConcreteGenericTypeParams(
                    genericTypeParamTypeHashMap, expectedArgTypes.get(i), actualArgTypes.get(i), inferConcreteTypes));
          }
          if (functionExpectedArgType.baseType().equals(BaseType.CONSUMER_FUNCTION)) {
            return Types.ProcedureType.ConsumerType.typeLiteralForConsumerArgTypes(
                validatedArgTypes.build(), ((Types.ProcedureType) functionExpectedArgType).getAnnotatedBlocking());
          }
          return Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
              validatedArgTypes.build(),
              validatedReturnType,
              ((Types.ProcedureType) functionExpectedArgType).getAnnotatedBlocking()
          );
        case FUTURE: // TODO(steving) Actually, all types should be able to be validated in this way... THIS is how I had originally set out to implement Types
        case LIST:   //  as nested structures that self-describe. If they all did this, there could be a single case instead of a switch.
        case TUPLE:
          ImmutableList<Type> expectedParameterizedArgTypes =
              functionExpectedArgType.parameterizedTypeArgs().values().asList();
          ImmutableList<Type> actualParameterizedArgTypes = actualArgExprType.parameterizedTypeArgs().values().asList();

          ImmutableList.Builder<Type> validatedParameterizedArgTypes = ImmutableList.builder();
          for (int i = 0; i < functionExpectedArgType.parameterizedTypeArgs().size(); i++) {
            validatedParameterizedArgTypes.add(
                validateArgExprsAndExtractConcreteGenericTypeParams(
                    genericTypeParamTypeHashMap,
                    expectedParameterizedArgTypes.get(i),
                    actualParameterizedArgTypes.get(i),
                    inferConcreteTypes
                ));
          }
          switch (functionExpectedArgType.baseType()) {
            case FUTURE:
              return Types.FutureType.wrapping(validatedParameterizedArgTypes.build().get(0));
            case LIST:
              return Types.ListType.forValueType(validatedParameterizedArgTypes.build().get(0));
            case TUPLE:
              return Types.TupleType.forValueTypes(validatedParameterizedArgTypes.build());
          }
        default:
          throw new ClaroParserException("Internal Compiler Error: I'm missing handling a case that requires structural type validation when validating a call to a generic function and inferring the concrete type params.");
      }
    } else {
      // Otherwise, this is not a generic type param position, and we need to validate this arg against the
      // actual concrete type in the function signature.
      if (!actualArgExprType.equals(functionExpectedArgType)) {
        throw DEFAULT_TYPE_MISMATCH_EXCEPTION;
      }
      return actualArgExprType;
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // TODO(steving) It would honestly be best to ensure that the "unused" checking ONLY happens in the type-checking
    // TODO(steving) phase, rather than having to be redone over the same code in the javasource code gen phase.
    // It's possible that during the process of monomorphization when we are doing type checking over a particular
    // signature, this function call might represent the identification of a new signature for a generic function that
    // needs monomorphization. In that case, this function's identifier may not be in the scoped heap yet and that's ok.
    if (!this.name.contains("$MONOMORPHIZATION")) {
      scopedHeap.markIdentifierUsed(this.name);
    } else {
      this.hashNameForCodegen = true;
    }

    if (this.hashNameForCodegen) {
      // In order to call the actual monomorphization, we need to ensure that the name isn't too long for Java.
      // So, we're following a hack where all monomorphization names are sha256 hashed to keep them short while
      // still unique.
      this.name =
          String.format(
              "%s__%s",
              this.originalName,
              Hashing.sha256().hashUnencodedChars(this.name).toString()
          );
    }

    AtomicReference<GeneratedJavaSource> exprsGenJavaSource =
        new AtomicReference<>(GeneratedJavaSource.forJavaSourceBody(new StringBuilder()));
    GeneratedJavaSource functionCallJavaSourceBody = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                "%s.apply(%s)",
                this.name,
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
                    .collect(Collectors.joining(", "))
            )
        )
    );

    // This node will be potentially reused assuming that it is called within a Generic function that gets
    // monomorphized as that process will reuse the exact same nodes over multiple sets of types. So reset
    // the name now.
    this.name = this.originalName;

    // We definitely don't want to be throwing away the static definitions and preambles required for the exprs
    // passed as args to this function call, so ensure that they're correctly collected and passed on here.
    return functionCallJavaSourceBody.createMerged(exprsGenJavaSource.get());
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
