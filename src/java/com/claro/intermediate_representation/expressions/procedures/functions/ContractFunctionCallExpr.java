package com.claro.intermediate_representation.expressions.procedures.functions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.contracts.ContractDefinitionStmt;
import com.claro.intermediate_representation.statements.contracts.ContractImplementationStmt;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureImplementationStmt;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureSignatureDefinitionStmt;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.*;
import com.google.common.hash.Hashing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ContractFunctionCallExpr extends FunctionCallExpr {
  private final String contractName;
  private final Expr contractNameForLogging;
  private final String procedureName;
  private final Expr procedureNameForLogging;
  private String referencedContractImplName;
  private ImmutableList<Type> resolvedContractConcreteTypes;
  private Types.$Contract resolvedContractType;
  private String originalName;
  private Type validatedOutputType;
  private boolean isDynamicDispatch = false;
  private Optional<ImmutableMap<String, Type>> requiredContextualOutputTypeAssertedTypes = Optional.empty();
  private HashMap<Type, Boolean> isTypeParamEverUsedWithinNestedCollectionTypeMap = Maps.newHashMap();
  private boolean dynamicDispatchCodegenRequiresCastBecauseOfJavasVeryLimitedTypeInference = false;
  // This node has a re-entrance problem when used within a monomorphized procedure as the type validation
  // will happen multiple times over this node for different concrete type sets. This boolean allows the
  // disambiguating between whether re-entrance is happening, or whether we just took the path through
  // contextual type assertion where some pre-computation happens early.
  private boolean typeValidationViaContextualAssertion;

  public ContractFunctionCallExpr(
      String contractName,
      Expr contractNameForLogging,
      String functionName,
      Expr functionNameForLogging,
      ImmutableList<Expr> args,
      Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(
        // For now, we'll just masquerade as function name since we haven't resolved types yet. But we'll need to
        // canonicalize the name in a moment after type validation has gotten under way and types are known.
        functionName, args, currentLine, currentLineNumber, startCol, endCol);

    this.contractName = contractName;
    this.contractNameForLogging = contractNameForLogging;
    this.procedureName = functionName;
    this.procedureNameForLogging = functionNameForLogging;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type assertedOutputType) throws ClaroTypeException {
    if (!this.resolveContractType(scopedHeap)) {
      // Some error message was already logged on the appropriate expr.
      throw new ClaroTypeException(Types.UNKNOWABLE, assertedOutputType);
    }

    // Before we do any type checking, we essentially want to disable worrying about exact matches on generic
    // type params. For a contract, we simply would want to know that the contract is listed in the requirements
    // but for its generic argument positions it can be passed literally any type (since the existence of that
    // contract impl will actually be validated at the callsite to the generic function, not here).
    Expr.validatingContractProcCallWithinGenericProc = true;

    ContractDefinitionStmt contractDefinitionStmt =
        (ContractDefinitionStmt) scopedHeap.getIdentifierValue(this.contractName);
    ContractProcedureSignatureDefinitionStmt contractProcedureSignatureDefinitionStmt =
        contractDefinitionStmt.declaredContractSignaturesByProcedureName.get(this.name);

    // Infer all types starting from the return type as necessary.
    HashMap<Type, Type> inferredConcreteTypes = Maps.newHashMap();
    // Let FunctionCallExpr that we're about to defer to handle throwing the exception if this was called over some
    // consumer that doesn't even have an output type.
    if (contractProcedureSignatureDefinitionStmt.resolvedOutputType.isPresent()) {
      // Need to infer the output type first to preserve the semantic that the contextual types provide better type
      // checking on the args necessary to get that desired output type.
      try {
        StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
            inferredConcreteTypes,
            contractProcedureSignatureDefinitionStmt.resolvedOutputType.get().toType(),
            assertedOutputType
        );
      } catch (ClaroTypeException ignored) {
        // We want cleaner error messages that indicate the type mismatch more clearly.
        throw new ClaroTypeException(
            assertedOutputType,
            contractProcedureSignatureDefinitionStmt.resolvedOutputType.get().toType()
        );
      }
      // Now, using the type information gained from the output type check, check all of the args.
      for (Integer i : contractProcedureSignatureDefinitionStmt.inferContractImplTypesFromArgsWhenContextualOutputTypeAsserted.get()) {
        try {
          StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
              inferredConcreteTypes,
              contractProcedureSignatureDefinitionStmt.resolvedArgTypes.get(i).toType(),
              this.argExprs.get(i).getValidatedExprType(scopedHeap),
              /*inferConcreteTypes=*/ false,
              /*optionalTypeCheckingCodegenForDynamicDispatch=*/ Optional.empty(),
              /*optionalTypeCheckingCodegenPath=*/ Optional.empty(),
              Optional.of(isTypeParamEverUsedWithinNestedCollectionTypeMap),
              /*withinNestedCollectionType=*/ false
          );
        } catch (ClaroTypeException ignored) {
          // We want cleaner error messages that indicate the type mismatch more clearly.
          Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages = Optional.of(inferredConcreteTypes);
          this.argExprs.get(i)
              .assertExpectedExprType(scopedHeap, contractProcedureSignatureDefinitionStmt.resolvedArgTypes.get(i)
                  .toType());
          Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages = Optional.empty();
        }
      }
      this.resolvedContractConcreteTypes = contractDefinitionStmt.typeParamNames.stream()
          .map(n -> inferredConcreteTypes.get(Types.$GenericTypeParam.forTypeParamName(n)))
          .collect(ImmutableList.toImmutableList());

      // Hold these simply to be able to codegen in the case of dynamic dispatch.
      if (contractProcedureSignatureDefinitionStmt.contextualOutputTypeAssertionRequired) {
        ImmutableMap.Builder<String, Type> requiredContextualOutputTypeAssertedTypesBuilder = ImmutableMap.builder();
        contractProcedureSignatureDefinitionStmt.requiredContextualOutputTypeAssertionTypeParamNames.forEach(
            n -> requiredContextualOutputTypeAssertedTypesBuilder.put(
                n, inferredConcreteTypes.get(Types.$GenericTypeParam.forTypeParamName(n))));
        this.requiredContextualOutputTypeAssertedTypes =
            Optional.of(requiredContextualOutputTypeAssertedTypesBuilder.build());
      }
    }

    this.validatedOutputType = assertedOutputType;
    this.typeValidationViaContextualAssertion = true;
    super.assertExpectedExprType(scopedHeap, assertedOutputType);
    this.typeValidationViaContextualAssertion = false;

    // Return type validation to the default state where Generic type params must be strictly checked for equality.
    Expr.validatingContractProcCallWithinGenericProc = false;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // We're handling both the case that this was called with a type statically asserted by the surrounding
    // context, and that it wasn't, so cleanup in the case that contract wasn't already looked up yet.
    if (this.resolvedContractType == null) {
      if (!this.resolveContractType(scopedHeap)) {
        // Some error message was already logged on the appropriate expr.
        return Types.UNKNOWABLE;
      }
    }

    ContractDefinitionStmt contractDefinitionStmt =
        (ContractDefinitionStmt) scopedHeap.getIdentifierValue(this.contractName);
    ContractProcedureSignatureDefinitionStmt contractProcedureSignatureDefinitionStmt =
        contractDefinitionStmt.declaredContractSignaturesByProcedureName.get(this.name);
    if (contractProcedureSignatureDefinitionStmt.resolvedOutputType.isPresent()) {
      if (this.validatedOutputType == null &&
          contractProcedureSignatureDefinitionStmt.contextualOutputTypeAssertionRequired) {
        if (!contractDefinitionStmt.impliedTypeParamNames.containsAll(
            contractProcedureSignatureDefinitionStmt.requiredContextualOutputTypeAssertionTypeParamNames)) {
          // In this case the program is ambiguous when the return type isn't asserted. The output type of this particular
          // Contract procedure call happens to be composed around a Contract type param that is NOT present in one of the
          // other args, therefore making Contract Impl inference via the arg types alone impossible.
          this.logTypeError(
              ClaroTypeException.forContractProcedureCallWithoutRequiredContextualOutputTypeAssertion(
                  this.contractName,
                  contractDefinitionStmt.typeParamNames,
                  this.name,
                  contractProcedureSignatureDefinitionStmt.resolvedOutputType.get().toType()
              ));
          return Types.UNKNOWABLE;
        }
      } else if (!contractProcedureSignatureDefinitionStmt.resolvedOutputType.get()
          .getGenericContractTypeParamsReferencedByType()
          .isEmpty()) {
        // Also hold this simply to know if a cast will be necessary for dynamic dispatch codegen (because Java's type
        // inference is *significantly* more limited than Claro's it needs a helping hand in cases where Claro doesn't).
        this.dynamicDispatchCodegenRequiresCastBecauseOfJavasVeryLimitedTypeInference = true;
      }
    }

    Type res = this.getValidatedTypeInternal(
        contractDefinitionStmt, contractProcedureSignatureDefinitionStmt, scopedHeap);

    // In case we actually would return a generic type here (bc we're still within a generic type validation phase), we
    // can just go ahead and assume it safe to return the asserted type. This is because we know that when the Generic
    // Procedure that this contract function call is made within is actually called, the correct monomorphization for
    // this contract will be selected since the generic procedure's contract requirements will have been validated.
    if (res.baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
      return this.validatedOutputType;
    }
    this.validatedOutputType = res;
    return res;
  }

  private Type getValidatedTypeInternal(
      ContractDefinitionStmt contractDefinitionStmt,
      ContractProcedureSignatureDefinitionStmt contractProcedureSignatureDefinitionStmt,
      ScopedHeap scopedHeap) throws ClaroTypeException {
    AtomicReference<String> procedureName_OUT_PARAM = new AtomicReference<>(this.name);
    AtomicReference<String> originalName_OUT_PARAM = new AtomicReference<>(this.originalName);
    AtomicReference<String> referencedContractImplName_OUT_PARAM =
        new AtomicReference<>(this.referencedContractImplName);
    AtomicReference<ImmutableList<Type>> resolvedContractConcreteTypes_OUT_PARAM =
        new AtomicReference<>(this.resolvedContractConcreteTypes);
    AtomicBoolean isDynamicDispatch_OUT_PARAM = new AtomicBoolean(this.isDynamicDispatch);
    try {
      getValidatedTypeInternal(
          this.contractName,
          this.resolvedContractType,
          contractDefinitionStmt,
          contractProcedureSignatureDefinitionStmt,
          this.argExprs,
          /*alreadyAssertedOutputTypes=*/ this.typeValidationViaContextualAssertion,
          Optional.of(this::logTypeError),
          scopedHeap,
          this.isTypeParamEverUsedWithinNestedCollectionTypeMap,
          resolvedContractConcreteTypes_OUT_PARAM,
          procedureName_OUT_PARAM,
          originalName_OUT_PARAM,
          referencedContractImplName_OUT_PARAM,
          isDynamicDispatch_OUT_PARAM
      );
    } finally {
      this.name = procedureName_OUT_PARAM.get();
      this.originalName = originalName_OUT_PARAM.get();
      this.referencedContractImplName = referencedContractImplName_OUT_PARAM.get();
      this.resolvedContractConcreteTypes = resolvedContractConcreteTypes_OUT_PARAM.get();
      this.isDynamicDispatch = isDynamicDispatch_OUT_PARAM.get();
      // Need to ensure that this is reset for the next run in case this is called within a monomorphization.
      this.isTypeParamEverUsedWithinNestedCollectionTypeMap.clear();
    }
    // This final step defers validation of the actual types passed as args.
    Type res = super.getValidatedExprType(scopedHeap);
    if (InternalStaticStateUtil.GnericProcedureDefinitionStmt_withinGenericProcedureDefinitionTypeValidation) {
      this.name = originalName_OUT_PARAM.get();
    }
    return res;
  }


  public static void getValidatedTypeInternal(
      final String contractName,
      final Types.$Contract resolvedContractType,
      ContractDefinitionStmt contractDefinitionStmt,
      ContractProcedureSignatureDefinitionStmt contractProcedureSignatureDefinitionStmt,
      ImmutableList<Expr> argExprs,
      boolean alreadyAssertedOutputTypes,
      final Optional<Consumer<Exception>> optionalLogTypeError,
      final ScopedHeap scopedHeap,
      HashMap<Type, Boolean> isTypeParamEverUsedWithinNestedCollectionTypeMap,
      AtomicReference<ImmutableList<Type>> resolvedContractConcreteTypes_OUT_PARAM,
      AtomicReference<String> procedureName_OUT_PARAM,
      AtomicReference<String> originalName_OUT_PARAM,
      AtomicReference<String> referencedContractImplName_OUT_PARAM,
      AtomicBoolean isDynamicDispatch_OUT_PARAM
  ) throws ClaroTypeException {
    // Do type inference of the concrete types by checking only the necessary args.
    if (!alreadyAssertedOutputTypes || resolvedContractConcreteTypes_OUT_PARAM.get() == null) {
      HashMap<Type, Type> inferredConcreteTypes = Maps.newHashMap();
      // It's actually possible to fully infer the Contract Type Params based strictly off arg type inference.
      for (Integer i : contractProcedureSignatureDefinitionStmt.inferContractImplTypesFromArgs) {
        Type actualArgExprType = argExprs.get(i).getValidatedExprType(scopedHeap);
        try {
          StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
              inferredConcreteTypes,
              contractProcedureSignatureDefinitionStmt.resolvedArgTypes.get(i).toType(),
              actualArgExprType,
              /*inferConcreteTypes=*/ false,
              /*optionalTypeCheckingCodegenForDynamicDispatch=*/ Optional.empty(),
              /*optionalTypeCheckingCodegenPath=*/ Optional.empty(),
              Optional.of(isTypeParamEverUsedWithinNestedCollectionTypeMap),
              /*withinNestedCollectionType=*/ false
          );
        } catch (ClaroTypeException ignored) {
          // We want cleaner error messages that indicate the type mismatch more clearly.
          throw new ClaroTypeException(
              actualArgExprType,
              contractProcedureSignatureDefinitionStmt.resolvedArgTypes.get(i).toType()
          );
        }
      }

      // Handle case where this is actually a situation where the generic return type must be inferred from the contract
      // definition's implied type constraints.
      if (contractProcedureSignatureDefinitionStmt.contextualOutputTypeAssertionRequired &&
          contractDefinitionStmt.impliedTypeParamNames.containsAll(
              contractProcedureSignatureDefinitionStmt.requiredContextualOutputTypeAssertionTypeParamNames)) {
        // Need to infer the output type first to preserve the semantic that the contextual types provide better type
        // checking on the args necessary to get that desired output type.
        int firstImpliedTypeInd =
            contractDefinitionStmt.typeParamNames
                .indexOf(contractDefinitionStmt.impliedTypeParamNames.asList().get(0));
        ImmutableList<Type> contractConcreteTypeParamsInferredFromArgs =
            contractDefinitionStmt.typeParamNames.subList(0, firstImpliedTypeInd).stream()
                .map(n -> inferredConcreteTypes.get(Types.$GenericTypeParam.forTypeParamName(n)))
                .collect(ImmutableList.toImmutableList());
        ContractDefinitionStmt.contractImplementationsByContractName.get(contractName).stream()
            .filter(
                m -> m.values().asList().subList(0, firstImpliedTypeInd)
                    .equals(contractConcreteTypeParamsInferredFromArgs))
            .findFirst()
            .get().entrySet().stream()
            .filter(e -> contractDefinitionStmt.impliedTypeParamNames.contains(e.getKey()))
            .forEach(e -> inferredConcreteTypes.put(Types.$GenericTypeParam.forTypeParamName(e.getKey()), e.getValue()));
      }

      // We've finished inferring all the Concrete Contract Type Params, preserve them in the out param.
      resolvedContractConcreteTypes_OUT_PARAM.set(
          contractDefinitionStmt.typeParamNames.stream()
              .map(n -> inferredConcreteTypes.get(Types.$GenericTypeParam.forTypeParamName(n)))
              .collect(ImmutableList.toImmutableList()));
    }

    // If this contract procedure is getting called over any generic type params, then we need to validate that the
    // generic function it's getting called within actually already marks this particular contract impl as `required`.
    CheckContractImplAnnotatedRequiredWithinGenericFunctionDefinition:
    {
      if (resolvedContractConcreteTypes_OUT_PARAM.get().stream()
          .anyMatch(contractTypeParam -> contractTypeParam.baseType().equals(BaseType.$GENERIC_TYPE_PARAM))) {
        Optional optionalRequiredContractNamesToGenericArgs =
            Optional.ofNullable(
                ((Types.ProcedureType) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureResolvedType
                    .get())
                    .getAllTransitivelyRequiredContractNamesToGenericArgs());
        if (!optionalRequiredContractNamesToGenericArgs.isPresent()) {
          // In the case that we're within a lambda expr defined w/in a generic procedure definition, we need to grab
          // the required contract impls info from a different source.
          optionalRequiredContractNamesToGenericArgs =
              InternalStaticStateUtil.LambdaExpr_optionalActiveGenericProcedureDefRequiredContractNamesToGenericArgs;
        }
        if (optionalRequiredContractNamesToGenericArgs.isPresent()
            && !((ListMultimap<String, ImmutableList<Types.$GenericTypeParam>>)
                     optionalRequiredContractNamesToGenericArgs.get()).isEmpty()) {
          // There are actually some contracts annotated required, let's look for one that would match the current call.
          for (ImmutableList<Types.$GenericTypeParam> annotatedRequiredContractImplTypes :
              ((ListMultimap<String, ImmutableList<Types.$GenericTypeParam>>)
                   optionalRequiredContractNamesToGenericArgs.get()).get(contractName)) {
            if (annotatedRequiredContractImplTypes.equals(resolvedContractConcreteTypes_OUT_PARAM.get())) {
              // Good job programmer!
              break CheckContractImplAnnotatedRequiredWithinGenericFunctionDefinition;
            }
          }
        }
        // Let's not make this a terminal exception that prevents continuing type checking. Just mark the error and
        // continue on with type checking to find more errors.
        ClaroTypeException badContractReferenceWithoutRequiresAnnotation =
            ClaroTypeException.forContractProcedureReferencedWithoutRequiredAnnotationOnGenericFunction(
                contractName, procedureName_OUT_PARAM.get(), resolvedContractConcreteTypes_OUT_PARAM.get());
        if (optionalLogTypeError.isPresent()) {
          optionalLogTypeError.get()
              .accept(badContractReferenceWithoutRequiresAnnotation);
        } else {
          throw badContractReferenceWithoutRequiresAnnotation;
        }
      }
    }

    // Set the canonical implementation name so that this can be referenced later simply by type.
    ImmutableList<String> concreteTypeStrings =
        resolvedContractConcreteTypes_OUT_PARAM.get().stream()
            .map(Type::toString)
            .collect(ImmutableList.toImmutableList());

    // Check that there are the correct number of type params.
    if (resolvedContractType.getTypeParamNames().size() != resolvedContractConcreteTypes_OUT_PARAM.get().size()) {
      throw ClaroTypeException.forContractReferenceWithWrongNumberOfTypeParams(
          ContractImplementationStmt.getContractTypeString(contractName, concreteTypeStrings),
          ContractImplementationStmt.getContractTypeString(contractName, resolvedContractType.getTypeParamNames())
      );
    }

    // It's actually possible that this contract procedure is being called with all generic argument types. This is only
    // possible when type validation is being performed for a generic function when arg types are actually not known
    // yet. We'll just skip looking up the contract procedure in the scoped heap at that point and return the generic
    // type that would have aligned with the output type.
    originalName_OUT_PARAM.set(procedureName_OUT_PARAM.get());
    if (resolvedContractConcreteTypes_OUT_PARAM.get().stream()
        .anyMatch(concreteContractType -> concreteContractType.baseType().equals(BaseType.$GENERIC_TYPE_PARAM))) {
      // Here, we're just doing a sanity check of a generic function definition, since we're checking against generic
      // type params rather than concrete types, so, just want to do a lookup on the non-impl contract procedure name in
      // the symbol table rather than a "real" implementation.
      procedureName_OUT_PARAM.set(String.format("$%s::%s", contractName, procedureName_OUT_PARAM.get()));
    } else {
      // We can now resolve the contract's concrete types so that we can canonicalize the function call name.
      procedureName_OUT_PARAM.set(
          ContractProcedureImplementationStmt.getCanonicalProcedureName(
              contractName,
              resolvedContractConcreteTypes_OUT_PARAM.get(),
              procedureName_OUT_PARAM.get()
          )
      );

      String contractImplTypeString =
          ContractImplementationStmt.getContractTypeString(contractName, concreteTypeStrings);
      if (!scopedHeap.isIdentifierDeclared(contractImplTypeString)) {
        // So, we weren't able to find an implementation of the given type params. It's possible that we can find a
        // valid dispatch target if the call was passed any oneofs whose variants have impls. However, this is only
        // even possibly valid if some type params treated as oneofs are *NEVER* nested within structured collection
        // types (as then the oneof means something semantically different than dynamic dispatch).
        if (IntStream.range(0, contractDefinitionStmt.typeParamNames.size()).anyMatch(
            i -> resolvedContractConcreteTypes_OUT_PARAM.get().get(i).baseType().equals(BaseType.ONEOF)
                 && contractDefinitionStmt.contractProceduresSupportingDynamicDispatchOverArgs
                     .get(originalName_OUT_PARAM.get()).contains(i) &&
                 // Importantly, don't allow any Dynamic Dispatch attempts if the oneof is nested within any
                 // collection types, because in that case the semantics of dynamic dispatch aren't maintained,
                 // and it could lead to a runtime dispatch failure.
                 !isTypeParamEverUsedWithinNestedCollectionTypeMap.getOrDefault(
                     Types.$GenericTypeParam.forTypeParamName(contractDefinitionStmt.typeParamNames.get(i)), false)
        )) {
          List<String> requiredContractImplsForDynamicDispatchSupport =
              getAllDynamicDispatchConcreteContractProcedureNames(
                  contractName,
                  // Filter the set of supported dispatch args, based on the actual args having tried to nest a oneof.
                  contractDefinitionStmt.contractProceduresSupportingDynamicDispatchOverArgs
                      .get(originalName_OUT_PARAM.get()).stream()
                      .filter(i -> !isTypeParamEverUsedWithinNestedCollectionTypeMap.getOrDefault(
                          Types.$GenericTypeParam.forTypeParamName(contractDefinitionStmt.typeParamNames.get(i)),
                          false
                      ))
                      .collect(ImmutableList.toImmutableList()),
                  resolvedContractConcreteTypes_OUT_PARAM.get()
              );
          if (requiredContractImplsForDynamicDispatchSupport.stream().allMatch(scopedHeap::isIdentifierDeclared)) {
            // Dynamic dispatch should be supported for this call!
            isDynamicDispatch_OUT_PARAM.set(true);
            procedureName_OUT_PARAM.set(
                String.format("%s_DYNAMIC_DISPATCH_%s", contractName, originalName_OUT_PARAM.get()));
            referencedContractImplName_OUT_PARAM.set(String.format("$%s_DYNAMIC_DISPATCH_HANDLERS", contractName));
            if (contractProcedureSignatureDefinitionStmt.optionalGenericTypesList.isPresent()) {
              // Since we're about to allow FunctionCallExpr to take over the remaining type checking needs, we need to
              // setup a synthetic list of transitively required contracts for this dynamic dispatch call. This wasn't
              // already applied to the type in the ScopedHeap because I don't want to put a full join's worth of entries
              // in the heap to hold the correct subsets of transitive required contracts. So here, we'll update
              // temporarily so that this particular dyn disp call (which may be over a subset of the supported dyn disp
              // impls) may be validated against all the required contracts for the subsets of concrete type params
              // passed in as oneofs for this dyn disp call.
              ArrayListMultimap<String, ImmutableList<Type>>
                  transitivelyRequiredContractNamesToGenArgsForThisParticularDynDispCall = ArrayListMultimap.create();
              ImmutableList<ScopedHeap.IdentifierData> requiredContractImplsForDynamicDispatchSupportProcedureTypes =
                  requiredContractImplsForDynamicDispatchSupport.stream().map(
                      reqContractImpl ->
                          scopedHeap.getIdentifierData(
                              ContractProcedureImplementationStmt.getCanonicalProcedureName(
                                  contractName,
                                  ((Types.$ContractImplementation) scopedHeap.getValidatedIdentifierType(reqContractImpl))
                                      .getConcreteTypeParams(),
                                  originalName_OUT_PARAM.get()
                              ))
                  ).collect(ImmutableList.toImmutableList());
              for (ScopedHeap.IdentifierData reqContractImpl : requiredContractImplsForDynamicDispatchSupportProcedureTypes) {
                ArrayListMultimap<String, ImmutableList<Type>> reqContractImplTransitiveReqContracts =
                    ((Types.ProcedureType) reqContractImpl.type).getAllTransitivelyRequiredContractNamesToGenericArgs();
                reqContractImplTransitiveReqContracts.forEach(
                    (reqContractName, typeParams) ->
                        transitivelyRequiredContractNamesToGenArgsForThisParticularDynDispCall.get(reqContractName)
                            .add(typeParams));
              }
              ScopedHeap.IdentifierData dynDispatchProcedureIdentifierData = scopedHeap.getIdentifierData(
                  String.format("%s_DYNAMIC_DISPATCH_%s", contractName, originalName_OUT_PARAM.get()));
              ((Types.ProcedureType) dynDispatchProcedureIdentifierData.type)
                  .allTransitivelyRequiredContractNamesToGenericArgs
                  .set(transitivelyRequiredContractNamesToGenArgsForThisParticularDynDispCall);
              dynDispatchProcedureIdentifierData.interpretedValue =
                  (BiFunction<ScopedHeap, ImmutableMap<Type, Type>, String>)
                      (monomorphizationPrepScopedHeap, concreteTypeParams) -> {
                        for (ScopedHeap.IdentifierData reqContractImpl :
                            requiredContractImplsForDynamicDispatchSupportProcedureTypes) {
                          String unused =
                              ((BiFunction<ScopedHeap, ImmutableMap<Type, Type>, String>) reqContractImpl.interpretedValue)
                                  .apply(monomorphizationPrepScopedHeap, concreteTypeParams);
                        }
                        // For dyn dispatch there's only a single switch function which will dispatch to the
                        // monomorphizations.
                        return String.format("%s_DYNAMIC_DISPATCH_%s", contractName, originalName_OUT_PARAM.get());
                      };
            }
          } else {
            // Dynamic dispatch was requested but it's not supported for this call.
            throw ClaroTypeException.forContractProcedureDynamicDispatchCallOverUnsupportedContractTypeParams(
                contractImplTypeString,
                requiredContractImplsForDynamicDispatchSupport,
                requiredContractImplsForDynamicDispatchSupport.stream()
                    .filter(scopedHeap::isIdentifierDeclared)
                    .collect(ImmutableSet.toImmutableSet())
            );
          }
        } else if (IntStream.range(0, contractDefinitionStmt.typeParamNames.size())
            .anyMatch(
                i -> resolvedContractConcreteTypes_OUT_PARAM.get().get(i).baseType().equals(BaseType.ONEOF) &&
                     // Importantly, don't allow any Dynamic Dispatch attempts if the oneof is nested within any
                     // collection types, because in that case the semantics of dynamic dispatch aren't maintained,
                     // and it could lead to a runtime dispatch failure.
                     !isTypeParamEverUsedWithinNestedCollectionTypeMap.getOrDefault(
                         Types.$GenericTypeParam.forTypeParamName(contractDefinitionStmt.typeParamNames.get(i)), false)
            )) {
          ImmutableMap<String, Type> contextualAssertedOutputTypes =
              contractProcedureSignatureDefinitionStmt
                  .requiredContextualOutputTypeAssertionTypeParamNames.stream()
                  .collect(ImmutableMap.toImmutableMap(
                      t -> t,
                      t -> resolvedContractConcreteTypes_OUT_PARAM.get()
                          .get(contractDefinitionStmt.typeParamNames.indexOf(t))
                  ));
          List<String> requiredContractImpls =
              getAllDynamicDispatchConcreteContractProcedureNames(
                  contractName,
                  contractDefinitionStmt.contractProceduresSupportingDynamicDispatchOverArgsWhenGenericReturnTypeInferenceRequired
                      .get(originalName_OUT_PARAM.get())
                      .get(contextualAssertedOutputTypes).stream()
                      // Filter the set of supported dispatch args, based on the actual args having tried to nest a oneof.
                      .filter(i -> !isTypeParamEverUsedWithinNestedCollectionTypeMap.getOrDefault(
                          Types.$GenericTypeParam.forTypeParamName(contractDefinitionStmt.typeParamNames.get(i)),
                          false
                      ))
                      .collect(ImmutableList.toImmutableList()),
                  resolvedContractConcreteTypes_OUT_PARAM.get()
              );
          if (requiredContractImpls.stream().allMatch(scopedHeap::isIdentifierDeclared)) {
            // Dynamic dispatch should be supported for this call!
            isDynamicDispatch_OUT_PARAM.set(true);
            procedureName_OUT_PARAM.set(
                String.format(
                    "%s_$VARIANT$_%s_DYNAMIC_DISPATCH_%s",
                    contractName,
                    Hashing.sha256().hashUnencodedChars(
                        contextualAssertedOutputTypes.values().stream()
                            .map(Type::toString)
                            .collect(Collectors.joining("_"))
                    ).toString(),
                    originalName_OUT_PARAM.get()
                ));
            referencedContractImplName_OUT_PARAM.set(String.format("$%s_DYNAMIC_DISPATCH_HANDLERS", contractName));
          } else {
            List<String> requiredContractImplsForDynamicDispatchSupport =
                getAllDynamicDispatchConcreteContractProcedureNames(
                    contractName,
                    // I know that these are actually not all implemented, but the point is to indicate that these would
                    // be required IF you wanted to actually have this call supported.
                    IntStream.range(0, contractDefinitionStmt.typeParamNames.size())
                        .filter(i -> !contractProcedureSignatureDefinitionStmt
                            .requiredContextualOutputTypeAssertionTypeParamNames
                            .contains(contractDefinitionStmt.typeParamNames.get(i)))
                        .boxed()
                        // Filter the set of supported dispatch args, based on the actual args having tried to nest a oneof.
                        .filter(i -> !isTypeParamEverUsedWithinNestedCollectionTypeMap.getOrDefault(
                            Types.$GenericTypeParam.forTypeParamName(contractDefinitionStmt.typeParamNames.get(i)),
                            false
                        ))
                        .collect(ImmutableList.toImmutableList()),
                    resolvedContractConcreteTypes_OUT_PARAM.get()
                );
            // Dynamic dispatch was requested but it's not supported for this call.
            throw ClaroTypeException.forContractProcedureDynamicDispatchCallOverUnsupportedContractTypeParams(
                contractImplTypeString,
                requiredContractImplsForDynamicDispatchSupport,
                requiredContractImplsForDynamicDispatchSupport.stream()
                    .filter(scopedHeap::isIdentifierDeclared)
                    .collect(ImmutableSet.toImmutableSet())
            );
          }
        } else {
          // There's no indication that the user's attempting dynamic dispatch.
          throw ClaroTypeException.forContractProcedureCallOverUnimplementedConcreteTypes(contractImplTypeString);
        }
      } else {
        // Before leaving, just hold onto the corresponding contract's implementation name for codegen.
        referencedContractImplName_OUT_PARAM.set(
            (String) scopedHeap.getIdentifierValue(contractImplTypeString));
      }
    }
  }

  private boolean resolveContractType(ScopedHeap scopedHeap) {
    AtomicReference<Types.$Contract> resolvedContractType_OUT_PARAM =
        new AtomicReference<>(this.resolvedContractType);
    AtomicReference<String> procedureName_OUT_PARAM = new AtomicReference<>(this.name);
    AtomicReference<String> originalName_OUT_PARAM = new AtomicReference<>(this.originalName);
    try {
      return ContractFunctionCallExpr.resolveContractType(
          this.contractName, this.contractNameForLogging, this.procedureNameForLogging, scopedHeap,
          resolvedContractType_OUT_PARAM, procedureName_OUT_PARAM, originalName_OUT_PARAM
      );
    } finally {
      // To simulate "out params" no matter what happens in this call, the side effects must occur.
      this.resolvedContractType = resolvedContractType_OUT_PARAM.get();
      this.name = procedureName_OUT_PARAM.get();
      this.originalName = originalName_OUT_PARAM.get();
    }
  }

  // In order to get some code reuse across ContractFunctionCallExpr and ContractConsumerCallStmt I unfortunately need
  // to enable side effects to propagate to both callers. This hacked "out params" design is simply because Java doesn't
  // give me any language level mechanism for this single procedure to have the desired side effect on both types...
  // Claro will fix this by providing monomorphized generics over fields.
  public static boolean resolveContractType(
      String contractName,
      Expr contractNameForLogging,
      Expr procedureNameForLogging,
      ScopedHeap scopedHeap,
      // Java is garbage so there's no painless way to do anything resembling multiple returns, so instead here's this
      // gnarly hack. Taking in atomic refs so that I can simply use these as "out params" for lack of a better
      // mechanism w/ language support....
      AtomicReference<Types.$Contract> resolvedContractType_OUT_PARAM,
      AtomicReference<String> procedureName_OUT_PARAM,
      AtomicReference<String> originalName_OUT_PARAM) {
    // Validate that the contract name is valid.
    if (!scopedHeap.isIdentifierDeclared(contractName)) {
      contractNameForLogging.logTypeError(ClaroTypeException.forReferencingUnknownContract(contractName));
      return false;
    }
    Type contractType = scopedHeap.getValidatedIdentifierType(contractName);
    if (!contractType.baseType().equals(BaseType.$CONTRACT)) {
      contractNameForLogging.logTypeError(new ClaroTypeException(contractType, BaseType.$CONTRACT));
      return false;
    }
    resolvedContractType_OUT_PARAM.set((Types.$Contract) contractType);

    // Validate that the referenced procedure name is even actually in the referenced contract.
    if (!resolvedContractType_OUT_PARAM.get().getProcedureNames().contains(
        originalName_OUT_PARAM.get() == null ? procedureName_OUT_PARAM.get() : originalName_OUT_PARAM.get())) {
      procedureNameForLogging.logTypeError(
          ClaroTypeException.forContractReferenceUndefinedProcedure(
              contractName, resolvedContractType_OUT_PARAM.get().getTypeParamNames(), procedureName_OUT_PARAM.get()));
      return false;
    }

    // Ensure that we're starting off with a clean slate in regards to this.name being set to the original procedure
    // name. This is in the case that this contract function call is actually happening within a blocking-generic
    // procedure body, in which case the same ContractFunctionCallExpr node is being reused for type checking.
    if (originalName_OUT_PARAM.get() != null) {
      procedureName_OUT_PARAM.set(originalName_OUT_PARAM.get());
      originalName_OUT_PARAM.set(null);
    }
    return true;
  }

  private static List<String> getAllDynamicDispatchConcreteContractProcedureNames(
      String contractName,
      ImmutableCollection<Integer> dynamicDispatchSupportedOverTypeParamIndices,
      ImmutableList<Type> types) {
    return getAllDynamicDispatchConcreteContractProcedureNames_helper(
        contractName, dynamicDispatchSupportedOverTypeParamIndices, types, new ArrayList<>(), 0);
  }

  private static ArrayList<String> getAllDynamicDispatchConcreteContractProcedureNames_helper(
      String contractName,
      ImmutableCollection<Integer> dynamicDispatchSupportedOverTypeParamIndices,
      ImmutableList<Type> types,
      ArrayList<Type> currTypesResolvedVariants,
      int i) {
    ArrayList<String> res = new ArrayList<>();
    if (i >= types.size()) {
      ImmutableList<String> concreteTypeStrings =
          currTypesResolvedVariants.stream().map(Type::toString).collect(ImmutableList.toImmutableList());
      res.add(ContractImplementationStmt.getContractTypeString(contractName, concreteTypeStrings));
    } else if (dynamicDispatchSupportedOverTypeParamIndices.contains(i)
               && types.get(i).baseType().equals(BaseType.ONEOF)) {
      for (Type oneofVariant : ((Types.OneofType) types.get(i)).getVariantTypes()) {
        currTypesResolvedVariants.add(oneofVariant);
        res.addAll(
            getAllDynamicDispatchConcreteContractProcedureNames_helper(
                contractName, dynamicDispatchSupportedOverTypeParamIndices, types, currTypesResolvedVariants, i + 1));
        currTypesResolvedVariants.remove(currTypesResolvedVariants.size() - 1);
      }
    } else {
      currTypesResolvedVariants.add(types.get(i));
      res.addAll(
          getAllDynamicDispatchConcreteContractProcedureNames_helper(
              contractName, dynamicDispatchSupportedOverTypeParamIndices, types, currTypesResolvedVariants, i + 1));
      currTypesResolvedVariants.remove(currTypesResolvedVariants.size() - 1);
    }
    return res;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res =
        GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder(
                Optional.ofNullable(ScopedHeap.currProgramDepModules.rowMap().get("$THIS_MODULE$"))
                    .map(m -> m.values().stream().findFirst().get())
                    .map(d -> String.format("%s.%s.", d.getProjectPackage(), d.getUniqueModuleName()))
                    .orElse(""))
                .append(this.referencedContractImplName).append('.'));

    // In order to avoid using names that are way too long for Java, in the case of statically dispatched contract
    // procedure calls, we're going to hash all names within this contract implementation. I won't worry about
    // maintaining the old names here, because these variables should never be referenced anymore after codegen.
    super.hashNameForCodegen = !this.isDynamicDispatch;
    super.staticDispatchCodegen = this.isDynamicDispatch;
    super.optionalExtraArgsCodegen = this.requiredContextualOutputTypeAssertedTypes.map(
        outputTypes -> outputTypes.values().stream()
            .map(t -> {
              if (t instanceof ConcreteType) {
                return t.baseType().nativeJavaSourceImplClazz.get().getSimpleName() + ".class";
              }
              return t.getJavaSourceClaroType();
            }).collect(Collectors.joining(", "))
    );
    super.optionalConcreteGenericTypeParams.ifPresent(
        types -> super.optionalExtraArgsCodegen =
            Optional.of(super.optionalExtraArgsCodegen.map(s -> s + ", ").orElse("")
                        + types.stream()
                            .map(t -> t instanceof ConcreteType ?
                                      ((ConcreteType) t).baseType().nativeJavaSourceImplClazz.get().getSimpleName() +
                                      ".class" : t.getJavaSourceClaroType())
                            .collect(Collectors.joining(", "))));
    if (this.name.contains("$VARIANT$")) {
      this.name = String.format("%s_DYNAMIC_DISPATCH_%s", this.contractName, this.originalName);
    }

    res = res.createMerged(super.generateJavaSourceOutput(scopedHeap));

    if (this.isDynamicDispatch && this.dynamicDispatchCodegenRequiresCastBecauseOfJavasVeryLimitedTypeInference) {
      res = GeneratedJavaSource.forJavaSourceBody(
              new StringBuilder("((")
                  .append(this.validatedOutputType.getJavaSourceType())
                  .append(") "))
          .createMerged(res);
      res.javaSourceBody().append(")");
    }

    // This node will be potentially reused assuming that it is called within a Generic function that gets
    // monomorphized as that process will reuse the exact same nodes over multiple sets of types. So reset
    // the name now.
    this.name = this.originalName;

    return res;
  }
}
