package com.claro.intermediate_representation.expressions.procedures.functions;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.ProcedureDefinitionStmt;
import com.claro.intermediate_representation.statements.UsingBlockStmt;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FunctionCallExpr extends Expr {
  public String name;
  public final ImmutableList<Expr> argExprs;
  private Type assertedOutputTypeForGenericFunctionCallUse;

  public FunctionCallExpr(String name, ImmutableList<Expr> args, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.name = name;
    this.argExprs = args;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    // We simply want to make note of the type that was asserted on this function call for the sake of
    // GENERIC FUNCTION CALLS ONLY. For all other concrete function calls this will be ignored since we
    // actually definitively know the return type from concrete function calls.
    this.assertedOutputTypeForGenericFunctionCallUse = expectedExprType;
    super.assertExpectedExprType(scopedHeap, expectedExprType);
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
      // We're calling a generic function which means that we need to validate that the generic function's
      // requirements are upheld.
      // First, we'll check that the args match the ordering pattern of the generic signature.
      HashMap<Types.$GenericTypeParam, Type> genericTypeParamTypeHashMap = Maps.newHashMap();
      for (int i = 0; i < argExprs.size(); i++) {
        Type argType = ((Types.ProcedureType) referencedIdentifierType).getArgTypes().get(i);
        if (argType.baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
          // In the case that this positional arg is a generic param type, then actually we need to just accept
          // whatever type is in the passed arg expr.
          if (genericTypeParamTypeHashMap.containsKey(argType)) {
            // The type of this particular generic param has already been determined by an earlier arg over the
            // same generic type param, so actually this arg expr MUST have the same type.
            argExprs.get(i).assertExpectedExprType(scopedHeap, genericTypeParamTypeHashMap.get(argType));
          } else {
            genericTypeParamTypeHashMap.put(
                (Types.$GenericTypeParam) argType, argExprs.get(i).getValidatedExprType(scopedHeap));
          }
        } else {
          // Otherwise, this is not a generic type param position, and we need to validate this arg against the
          // actual concrete type in the function signature.
          argExprs.get(i).assertExpectedExprType(scopedHeap, argType);
        }
      }
      // Now, we need to check if the output type should have been constrained by the arg types, or by the
      // surrounding context.
      if (calledFunctionReturnType.baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
        if (genericTypeParamTypeHashMap.containsKey(calledFunctionReturnType)) {
          // In this case, the full concrete signature of this generic function is known (including the return type)
          // just by the validated arg expr types.
          calledFunctionReturnType = genericTypeParamTypeHashMap.get(calledFunctionReturnType);
        } else {
          // TODO(steving) Add a specific error message for this not being constrained.
          if (this.assertedOutputTypeForGenericFunctionCallUse == null) {
            throw new RuntimeException("Internal Compiler Error! TODO(steving) Still need to implement a proper error message for when generic function w/ generic return type doesn't have output type constrained by context.");
          }
          // I need to put this concrete type into the generic type map.
          genericTypeParamTypeHashMap.put(
              (Types.$GenericTypeParam) calledFunctionReturnType, this.assertedOutputTypeForGenericFunctionCallUse);
          calledFunctionReturnType = this.assertedOutputTypeForGenericFunctionCallUse;
        }
      }
      // Finally, need to ensure that the required contracts are supported by the requested concrete types
      // otherwise this would be an invalid call to the generic procedure.
      ImmutableMap<String, ImmutableList<Types.$GenericTypeParam>> genericFunctionRequiredContractsMap =
          ((Types.ProcedureType) referencedIdentifierType).getOptionalRequiredContractNamesToGenericArgs().get();
      for (String requiredContract : genericFunctionRequiredContractsMap.keySet()) {
        // TODO(steving) Do this type validation of the required contracts.
      }

      // I want to do Monomorphization for this type right away! Super exciting Claro feature here...straight up code gen within code gen....damn.
      this.name =
          ((BiFunction<ScopedHeap, HashMap<Types.$GenericTypeParam, Type>, String>)
               scopedHeap.getIdentifierValue(this.name)).apply(scopedHeap, genericTypeParamTypeHashMap);
    } else {
      // Validate that all of the given parameter Exprs are of the correct type.
      for (int i = 0; i < this.argExprs.size(); i++) {
        Expr currArgExpr = this.argExprs.get(i);
        // We have to handle the case where the defined arg type is actually a generic param, in which case
        // we're actually currently validating against a generic function definition body and only care that
        // the type we're passing in is also a generic type (since the correct type will be validated at the
        // call site).
        if (definedArgTypes.get(i).baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
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

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // TODO(steving) It would honestly be best to ensure that the "unused" checking ONLY happens in the type-checking
    // TODO(steving) phase, rather than having to be redone over the same code in the javasource code gen phase.
    scopedHeap.markIdentifierUsed(this.name);

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
