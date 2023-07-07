package com.claro.intermediate_representation.statements.contracts;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.procedures.functions.StructuralConcreteGenericTypeValidationUtil;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ContractProcedureSignatureDefinitionStmt extends Stmt {
  public final String procedureName;
  final Optional<ImmutableMap<String, TypeProvider>> optionalArgTypeProvidersByNameMap;
  private final Optional<TypeProvider> optionalOutputTypeProvider;
  private final Boolean explicitlyAnnotatedBlocking;
  private final Optional<ImmutableList<String>> optionalGenericBlockingOnArgs;
  private Optional<ImmutableSet<Integer>> optionalAnnotatedBlockingGenericOnArgs = Optional.empty();
  public Optional<ImmutableList<String>> optionalGenericTypesList;

  public ImmutableList<GenericSignatureType> resolvedArgTypes;
  public Optional<GenericSignatureType> resolvedOutputType;

  // The following fields provide ContractFunctionCall impl with a very quick lookup to determine the simplest set of
  // args it needs to do type inference on in order to determine the Contract Impl to dispatch to.
  public ImmutableSet<Integer> inferContractImplTypesFromArgs;
  public boolean contextualOutputTypeAssertionRequired;
  public Optional<ImmutableSet<Integer>> inferContractImplTypesFromArgsWhenContextualOutputTypeAsserted;
  public ImmutableSet<String> requiredContextualOutputTypeAssertionTypeParamNames = ImmutableSet.of();

  public ContractProcedureSignatureDefinitionStmt(
      String procedureName,
      Optional<ImmutableMap<String, TypeProvider>> optionalArgTypeProvidersByNameMap,
      Optional<TypeProvider> optionalOutputTypeProvider,
      Boolean explicitlyAnnotatedBlocking,
      Optional<ImmutableList<String>> optionalGenericBlockingOnArgs,
      Optional<ImmutableList<String>> optionalGenericTypesList) {
    super(ImmutableList.of());
    this.procedureName = procedureName;
    this.optionalArgTypeProvidersByNameMap = optionalArgTypeProvidersByNameMap;
    this.optionalOutputTypeProvider = optionalOutputTypeProvider;
    this.explicitlyAnnotatedBlocking = explicitlyAnnotatedBlocking;
    this.optionalGenericBlockingOnArgs = optionalGenericBlockingOnArgs;
    this.optionalGenericTypesList = optionalGenericTypesList;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // First, validate that this procedure name isn't already in use in this contract definition.
    String normalizedProcedureName =
        getFormattedInternalContractProcedureName(procedureName);
    Preconditions.checkState(
        !scopedHeap.isIdentifierDeclared(normalizedProcedureName),
        String.format(
            "Unexpected redeclaration of contract procedure %s<%s>::%s.",
            InternalStaticStateUtil.ContractDefinitionStmt_currentContractName,
            String.join(", ", InternalStaticStateUtil.ContractDefinitionStmt_currentContractGenericTypeParamNames),
            this.procedureName
        )
    );

    // Now we just need to resolve the arg types for the non-generic param typed args. The generic param types will
    // be made concrete at contract implementation time.
    if (this.optionalGenericTypesList.isPresent()) {
      for (String genericArgName : this.optionalGenericTypesList.get()) {
        Preconditions.checkState(
            !scopedHeap.isIdentifierDeclared(genericArgName),
            String.format(
                "Generic parameter name `%s` already in use for %s<%s>.",
                genericArgName,
                this.procedureName,
                String.join(", ", this.optionalGenericTypesList.get())
            )
        );
        // Temporarily stash a GenericTypeParam in the symbol table to be fetched by type resolution below.
        scopedHeap.putIdentifierValue(
            genericArgName,
            Types.$GenericTypeParam.forTypeParamName(genericArgName),
            null
        );
        scopedHeap.markIdentifierAsTypeDefinition(genericArgName);
      }
    }
    ImmutableList.Builder<GenericSignatureType> resolvedTypesBuilder = ImmutableList.builder();
    if (this.optionalArgTypeProvidersByNameMap.isPresent()) {
      for (Map.Entry<String, TypeProvider> argTypeByName : this.optionalArgTypeProvidersByNameMap.get().entrySet()) {
        Type resolvedArgType = argTypeByName.getValue().resolveType(scopedHeap);
        if (resolvedArgType.baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
          resolvedTypesBuilder.add(
              GenericSignatureType.forTypeParamName(
                  // All of these back flips of modeling a Type for generic type params was to be able to recover the
                  // param name here. Really this is working around a deficiency in the parser.
                  ((Types.$GenericTypeParam) resolvedArgType).getTypeParamName()));
        } else {
          resolvedTypesBuilder.add(GenericSignatureType.forResolvedType(resolvedArgType));
        }
      }
    }
    // Preserve the resolved signature types so that we can validate against this for contract impls later on.
    this.resolvedArgTypes = resolvedTypesBuilder.build();
    if (this.optionalOutputTypeProvider.isPresent()) {
      Type resolvedOutputType = this.optionalOutputTypeProvider.get().resolveType(scopedHeap);
      if (resolvedOutputType.baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
        this.resolvedOutputType =
            Optional.of(
                GenericSignatureType.forTypeParamName(
                    ((Types.$GenericTypeParam) resolvedOutputType).getTypeParamName()));
      } else {
        this.resolvedOutputType = Optional.of(GenericSignatureType.forResolvedType(resolvedOutputType));
      }
    } else {
      this.resolvedOutputType = Optional.empty();
    }
    // Drop the temporary GenericTypeParams placed in the symbol table just for the sake of the generic args.
    this.optionalGenericTypesList.ifPresent(
        l -> l.forEach(scopedHeap::deleteIdentifierValue)
    );

    // Later on, when this Contract procedure actually gets called, in order to determine which Contract Impl to
    // specifically defer to, we'll need to do type inference on the concrete args passed on that specific call. We will
    // end up following that Contract Impl inference by actual Procedure call type validation. So, when we're doing
    // Contract Impl inference, we want to check the *least* number of types that would give us the validated type info
    // necessary to defer to the correct Contract Impl. So, here we'll make note of which args are *necessary* to check
    // when inferring the Contract Impl to defer to.
    HashMap<String, Integer> leftmostArgReferencingContractTypeParams = Maps.newHashMap();
    Set<String> contractTypeParamNames =
        Sets.newHashSet(InternalStaticStateUtil.ContractDefinitionStmt_currentContractGenericTypeParamNames);
    for (int i = 0; i < this.resolvedArgTypes.size() && contractTypeParamNames.size() > 0; i++) {
      for (Types.$GenericTypeParam referencedGenType :
          this.resolvedArgTypes.get(i).getGenericContractTypeParamsReferencedByType()) {
        if (contractTypeParamNames.contains(referencedGenType.getTypeParamName())) {
          leftmostArgReferencingContractTypeParams.put(referencedGenType.getTypeParamName(), i);
          contractTypeParamNames.remove(referencedGenType.getTypeParamName());
        }
      }
    }
    this.inferContractImplTypesFromArgs = ImmutableSet.copyOf(leftmostArgReferencingContractTypeParams.values());
    this.contextualOutputTypeAssertionRequired =
        this.resolvedOutputType.isPresent() && contractTypeParamNames.size() > 0;
    if (this.contextualOutputTypeAssertionRequired) {
      this.requiredContextualOutputTypeAssertionTypeParamNames = ImmutableSet.copyOf(contractTypeParamNames);
    }
    this.inferContractImplTypesFromArgsWhenContextualOutputTypeAsserted =
        this.resolvedOutputType.map(
            resolvedOutputType ->
                leftmostArgReferencingContractTypeParams.entrySet()
                    .stream()
                    .filter(
                        e ->
                            !resolvedOutputType
                                .getGenericContractTypeParamsReferencedByType()
                                .contains(Types.$GenericTypeParam.forTypeParamName(e.getKey())))
                    .map(Map.Entry::getValue)
                    .collect(ImmutableSet.toImmutableSet()));


    // Now put the signature in the scoped heap so that we can validate it's not reused in this contract.
    Type procedureType;
    if (this.resolvedArgTypes.size() > 0) {
      ImmutableList<String> argNames = this.optionalArgTypeProvidersByNameMap.get().keySet().asList();
      this.optionalAnnotatedBlockingGenericOnArgs =
          this.optionalGenericBlockingOnArgs.map(
              l -> l.stream()
                  .map(argNames::indexOf)
                  .collect(ImmutableSet.toImmutableSet()));
      if (this.resolvedOutputType.isPresent()) {
        procedureType = Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
            this.resolvedArgTypes.stream()
                .map(GenericSignatureType::toType)
                .collect(ImmutableList.toImmutableList()),
            this.resolvedOutputType.get().toType(),
            this.explicitlyAnnotatedBlocking,
            this.optionalAnnotatedBlockingGenericOnArgs,
            this.optionalGenericTypesList
        );
      } else { // Consumer.
        procedureType = Types.ProcedureType.ConsumerType.typeLiteralForConsumerArgTypes(
            this.resolvedArgTypes.stream()
                .map(GenericSignatureType::toType)
                .collect(ImmutableList.toImmutableList()),
            this.explicitlyAnnotatedBlocking,
            this.optionalAnnotatedBlockingGenericOnArgs,
            this.optionalGenericTypesList
        );
      }
    } else { // Provider.
      procedureType = Types.ProcedureType.ProviderType.typeLiteralForReturnType(
          this.resolvedOutputType.get().toType(),
          this.explicitlyAnnotatedBlocking
      );
    }
    scopedHeap.putIdentifierValue(normalizedProcedureName, procedureType);
    scopedHeap.markIdentifierUsed(normalizedProcedureName);
  }

  public static String getFormattedInternalContractProcedureName(String procedureName) {
    return String.format(
        "$%s::%s",
        InternalStaticStateUtil.ContractDefinitionStmt_currentContractName,
        procedureName
    );
  }

  public Types.ProcedureType getExpectedProcedureTypeForConcreteTypeParams(ImmutableMap<String, Type> concreteTypeParams) {
    HashMap<Type, Type> typeParamsForInferenceMap = Maps.newHashMap();
    ImmutableList<Type> concreteArgTypes =
        getConcreteArgTypesForConcreteContractTypeParams(concreteTypeParams, typeParamsForInferenceMap);

    Optional<Type> optionalConcreteReturnType =
        getConcreteOutputTypesForConcreteContractTypeParams(typeParamsForInferenceMap);

    Types.ProcedureType resType =
        getProcedureTypeFromConcreteArgTypesAndOptionalReturnType(concreteArgTypes, optionalConcreteReturnType);
    return resType;
  }

  public Types.ProcedureType getProcedureTypeFromConcreteArgTypesAndOptionalReturnType(
      ImmutableList<Type> concreteArgTypes, Optional<Type> optionalConcreteReturnType) {
    Types.ProcedureType resType;
    if (concreteArgTypes.size() > 0) {
      if (optionalConcreteReturnType.isPresent()) {
        resType = Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
            concreteArgTypes,
            optionalConcreteReturnType.get(),
            this.explicitlyAnnotatedBlocking,
            this.optionalAnnotatedBlockingGenericOnArgs,
            this.optionalGenericTypesList
        );
      } else {
        resType = Types.ProcedureType.ConsumerType.typeLiteralForConsumerArgTypes(
            concreteArgTypes,
            this.explicitlyAnnotatedBlocking,
            this.optionalAnnotatedBlockingGenericOnArgs,
            this.optionalGenericTypesList
        );
      }
    } else {
      resType = Types.ProcedureType.ProviderType.typeLiteralForReturnType(
          optionalConcreteReturnType.get(), this.explicitlyAnnotatedBlocking);
    }
    return resType;
  }

  public ImmutableList<Type> getConcreteArgTypesForConcreteContractTypeParams(
      ImmutableMap<String, Type> concreteTypeParams, HashMap<Type, Type> typeParamsForInferenceMap) {
    ImmutableList.Builder<Type> concreteArgTypesBuilder = ImmutableList.builder();
    typeParamsForInferenceMap.putAll(
        concreteTypeParams.entrySet().stream().collect(
            ImmutableMap.toImmutableMap(
                e -> Types.$GenericTypeParam.forTypeParamName(e.getKey()),
                Map.Entry::getValue
            )));
    this.optionalGenericTypesList.ifPresent(
        genTypes -> typeParamsForInferenceMap.putAll(
            genTypes.stream().collect(
                ImmutableMap.toImmutableMap(
                    Types.$GenericTypeParam::forTypeParamName,
                    Types.$GenericTypeParam::forTypeParamName
                ))));
    for (GenericSignatureType genericSignatureType : this.resolvedArgTypes) {
      if (genericSignatureType.getGenericContractTypeParamsReferencedByType().isEmpty()) {
        concreteArgTypesBuilder.add(genericSignatureType.toType());
      } else {
        try {
          concreteArgTypesBuilder.add(
              StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
                  typeParamsForInferenceMap,
                  genericSignatureType.toType(),
                  genericSignatureType.toType(),
                  true
              )
          );
        } catch (ClaroTypeException e) {
          throw new RuntimeException("Internal Compiler Error: This should be unreachable!");
        }
      }
    }
    ImmutableList<Type> concreteArgTypes = concreteArgTypesBuilder.build();
    return concreteArgTypes;
  }

  public Optional<Type> getConcreteOutputTypesForConcreteContractTypeParams(
      HashMap<Type, Type> typeParamsForInferenceMap) {
    return this.resolvedOutputType.map(
        genericSignatureType ->
        {
          if (genericSignatureType.getGenericContractTypeParamsReferencedByType().isEmpty()) {
            return genericSignatureType.toType();
          }
          try {
            return StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
                typeParamsForInferenceMap,
                genericSignatureType.toType(),
                genericSignatureType.toType(),
                true
            );
          } catch (ClaroTypeException e) {
            throw new RuntimeException("Internal Compiler Error: This should be unreachable!");
          }
        }
    );
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return GeneratedJavaSource.forJavaSourceBody(new StringBuilder("/* TODO: IMPLEMENT CONTRACTS */"));
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return null;
  }

  @AutoValue
  public abstract static class GenericSignatureType {
    public abstract Optional<Type> getOptionalResolvedType();

    public abstract Optional<String> getOptionalGenericTypeParamName();

    public abstract ImmutableSet<Types.$GenericTypeParam> getGenericContractTypeParamsReferencedByType();

    public static GenericSignatureType forTypeParamName(String name) {
      return new AutoValue_ContractProcedureSignatureDefinitionStmt_GenericSignatureType(
          Optional.empty(), Optional.of(name), ImmutableSet.of(Types.$GenericTypeParam.forTypeParamName(name)));
    }

    public static GenericSignatureType forResolvedType(Type resolvedType) throws ClaroTypeException {
      HashMap<Type, Type> referencedGenericTypes = Maps.newHashMap();
      StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
          referencedGenericTypes,
          resolvedType,
          resolvedType
      );
      return new AutoValue_ContractProcedureSignatureDefinitionStmt_GenericSignatureType(
          Optional.of(resolvedType),
          Optional.empty(),
          referencedGenericTypes.keySet()
              .stream()
              .map(k -> (Types.$GenericTypeParam) k)
              .collect(ImmutableSet.toImmutableSet())
      );
    }

    public Type toType() {
      if (getOptionalResolvedType().isPresent()) {
        return getOptionalResolvedType().get();
      }
      return Types.$GenericTypeParam.forTypeParamName(getOptionalGenericTypeParamName().get());
    }
  }
}
