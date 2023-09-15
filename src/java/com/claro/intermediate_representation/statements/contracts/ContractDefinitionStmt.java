package com.claro.intermediate_representation.statements.contracts;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.procedures.functions.StructuralConcreteGenericTypeValidationUtil;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.google.common.hash.Hashing;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ContractDefinitionStmt extends Stmt {

  public final String contractName;

  private boolean alreadyAssertedTypes = false;
  public final ImmutableList<String> typeParamNames;
  public final ImmutableSet<String> impliedTypeParamNames;
  public final ImmutableMap<String, ContractProcedureSignatureDefinitionStmt> declaredContractSignaturesByProcedureName;

  public static Map<String, ArrayList<ImmutableMap<String, Type>>> contractImplementationsByContractName =
      Maps.newHashMap();
  public ImmutableMultimap<String, Integer> contractProceduresSupportingDynamicDispatchOverArgs =
      ImmutableMultimap.of();
  public ImmutableMap<String, ImmutableMultimap<ImmutableMap<String, Type>, Integer>>
      /*{ProcedureName -> {{TypeParamRequiredForContextualTypeAssertion -> Type} -> TypeParamIndex}}*/
      contractProceduresSupportingDynamicDispatchOverArgsWhenGenericReturnTypeInferenceRequired = ImmutableMap.of();

  public ContractDefinitionStmt(
      String contractName,
      ImmutableList<String> typeParamNames,
      ImmutableList<ContractProcedureSignatureDefinitionStmt> declaredContractSignatures) {
    super(ImmutableList.of());
    this.contractName = contractName;
    this.typeParamNames = typeParamNames;
    this.impliedTypeParamNames = ImmutableSet.of();
    this.declaredContractSignaturesByProcedureName =
        declaredContractSignatures.stream().collect(
            ImmutableMap.toImmutableMap(
                signature -> signature.procedureName,
                signature -> signature
            ));
  }

  public ContractDefinitionStmt(
      String contractName,
      ImmutableList<String> typeParamNames,
      ImmutableList<String> impliedTypeParamNames,
      ImmutableList<ContractProcedureSignatureDefinitionStmt> declaredContractSignatures) {
    super(ImmutableList.of());
    this.contractName = contractName;
    this.typeParamNames = ImmutableList.<String>builder().addAll(typeParamNames).addAll(impliedTypeParamNames).build();
    this.impliedTypeParamNames = ImmutableSet.copyOf(impliedTypeParamNames);
    this.declaredContractSignaturesByProcedureName =
        declaredContractSignatures.stream().collect(
            ImmutableMap.toImmutableMap(
                signature -> signature.procedureName,
                signature -> signature
            ));
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (!alreadyAssertedTypes) {
      alreadyAssertedTypes = true;

      // First, validate that this name is not already in use.
      Preconditions.checkState(
          !scopedHeap.isIdentifierDeclared(this.contractName),
          String.format("Unexpected redeclaration of contract %s.", this.contractName)
      );
      // Setup an empty set to collect all implementations in.
      ContractDefinitionStmt.contractImplementationsByContractName.put(this.contractName, new ArrayList<>());

      // Then, validate that none of the generic type param names are in use.
      for (String typeParamName : this.typeParamNames) {
        Preconditions.checkState(
            !scopedHeap.isIdentifierDeclared(typeParamName),
            String.format(
                "Generic parameter name `%s` already in use for %s<%s>.",
                typeParamName,
                this.contractName,
                String.join(", ", this.typeParamNames)
            )
        );
      }

      // Before anything else I need to setup the symbol table to include placeholder types for the generic params.
      InternalStaticStateUtil.ContractDefinitionStmt_currentContractName = this.contractName;
      InternalStaticStateUtil.ContractDefinitionStmt_currentContractGenericTypeParamNames = this.typeParamNames;
      for (String typeParamName : this.typeParamNames) {
        scopedHeap.putIdentifierValue(typeParamName, Types.$GenericTypeParam.forTypeParamName(typeParamName));
      }

      // Now I can validate each of the signatures defined within the contract body's scope.
      for (ContractProcedureSignatureDefinitionStmt signatureDefinitionStmt
          : this.declaredContractSignaturesByProcedureName.values()) {
        signatureDefinitionStmt.assertExpectedExprTypes(scopedHeap);
      }
      // Clean up the temporary generic type defs from the scoped heap.
      for (String typeParamName : this.typeParamNames) {
        scopedHeap.deleteIdentifierValue(typeParamName);
      }
      InternalStaticStateUtil.ContractDefinitionStmt_currentContractName = null;
      InternalStaticStateUtil.ContractDefinitionStmt_currentContractGenericTypeParamNames = null;

      // All the types check out, I can safely add this contract to the symbol table!
      scopedHeap.putIdentifierValue(
          this.contractName,
          Types.$Contract.forContractNameTypeParamNamesAndProcedureNames(
              this.contractName,
              this.typeParamNames,
              this.declaredContractSignaturesByProcedureName.keySet().asList()
          ),
          this
      );
      scopedHeap.markIdentifierUsed(this.contractName);
    }
  }

  @SuppressWarnings("unchecked")
  public void registerDynamicDispatchHandlers(ScopedHeap scopedHeap) {
    if (ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName).isEmpty()) {
      return;
    }
    ImmutableMultimap.Builder<String, Integer> contractProceduresSupportingDynamicDispatchOverArgs =
        ImmutableMultimap.builder();
    ImmutableMap.Builder<String, ImmutableMultimap<ImmutableMap<String, Type>, Integer>>
        contractProceduresSupportingDynamicDispatchOverArgsWhenGenericReturnTypeInferenceRequiredBuilder
        = ImmutableMap.builder();

    final Multimap<String, Type> contractGenTypeParamVariants = HashMultimap.create();
    if (this.declaredContractSignaturesByProcedureName.values().stream()
        .anyMatch(c -> !c.contextualOutputTypeAssertionRequired)) {
      // This will get used if there's at least one procedure that's not requiring generic return type inference.
      for (Map<String, Type> typeImplMapping :
          ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName)) {
        typeImplMapping.forEach(contractGenTypeParamVariants::put);
      }
    }

    for (Map.Entry<String, ContractProcedureSignatureDefinitionStmt> currSignatureByName :
        this.declaredContractSignaturesByProcedureName.entrySet()) {
      ImmutableMap.Builder<String, Type> contractTypeParamsAsVariantOneofsBuilder = ImmutableMap.builder();
      boolean isDynamicDispatchSupported = false;

      // In case the current procedure requires Generic Return Type Inference then we constrain the possible type params
      // that could be used to support dynamic dispatch.
      if (currSignatureByName.getValue().contextualOutputTypeAssertionRequired) {
        HashMap<ImmutableMap<String, Type>, Multimap<String, Type>>
            contractGenTypeParamVariantsByRequiredAssertedOutputTypeParamImpls = Maps.newHashMap();
        for (Map<String, Type> contractImpl :
            ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName)) {
          ImmutableMap<String, Type> requiredAssertedOutputTypeParamImpls = contractImpl.keySet().stream()
              .filter(t -> currSignatureByName.getValue().requiredContextualOutputTypeAssertionTypeParamNames.contains(t))
              .collect(ImmutableMap.toImmutableMap(
                  t -> t,
                  contractImpl::get
              ));
          Multimap<String, Type> currContractGenTypeParamVariants;
          if (contractGenTypeParamVariantsByRequiredAssertedOutputTypeParamImpls
              .containsKey(requiredAssertedOutputTypeParamImpls)) {
            currContractGenTypeParamVariants = contractGenTypeParamVariantsByRequiredAssertedOutputTypeParamImpls
                .get(requiredAssertedOutputTypeParamImpls);
          } else {
            currContractGenTypeParamVariants = HashMultimap.create();
            contractGenTypeParamVariantsByRequiredAssertedOutputTypeParamImpls
                .put(requiredAssertedOutputTypeParamImpls, currContractGenTypeParamVariants);
          }
          contractImpl.entrySet().stream()
              .filter(t -> !requiredAssertedOutputTypeParamImpls.containsKey(t.getKey()))
              .forEach(t -> currContractGenTypeParamVariants.put(t.getKey(), t.getValue()));
        }

        // Now, I know which return types are associated with which type params. Determine which are going to support
        // dynamic dispatch based on which arg type params have multiple impl'd variants.
        ImmutableMultimap.Builder<ImmutableMap<String, Type>, Integer>
            dynDispatchSupportedOverTypeArgsByRequiredReturnTypeParImpls = ImmutableMultimap.builder();
        boolean dynDispatchSupportedForAtLeastOneConcreteReturnType = false;
        for (Map.Entry<ImmutableMap<String, Type>, Multimap<String, Type>> genTypeVariantsByRequiredAssertedTypeParImpls
            : contractGenTypeParamVariantsByRequiredAssertedOutputTypeParamImpls.entrySet()) {
          for (int i = 0; i < this.typeParamNames.size(); i++) {
            String name = this.typeParamNames.get(i);
            ImmutableSet<Type> currTypeParamVariants =
                ImmutableSet.copyOf(genTypeVariantsByRequiredAssertedTypeParImpls.getValue().get(name));
            if (currSignatureByName.getValue().requiredContextualOutputTypeAssertionTypeParamNames.contains(name)) {
              contractTypeParamsAsVariantOneofsBuilder
                  .put(name, genTypeVariantsByRequiredAssertedTypeParImpls.getKey().get(name));
            } else if (currTypeParamVariants.size() <= 1) {
              // If there's only even a single concrete type variant or if the curr type param is one that requires
              // Generic Return Type Inference, then we don't support it for dynamic dispatch.
              contractTypeParamsAsVariantOneofsBuilder.put(name, currTypeParamVariants.asList().get(0));
            } else {
              isDynamicDispatchSupported = true;
              contractTypeParamsAsVariantOneofsBuilder.put(
                  name, Types.OneofType.forVariantTypes(currTypeParamVariants.asList()));
              // Make note of the fact that dynamic dispatch is supported specifically over this arg.
              dynDispatchSupportedOverTypeArgsByRequiredReturnTypeParImpls
                  .put(genTypeVariantsByRequiredAssertedTypeParImpls.getKey(), i);
            }
          }
          if (isDynamicDispatchSupported) {
            // In this case, Dynamic Dispatch can be supported over at least one of the Contract's Type Params.
            ImmutableList<Object> supportedConcreteArgTypesAndOutputType =
                getAllSupportedDynamicDispatchConcreteContractProcedureArgTypes(
                    this.declaredContractSignaturesByProcedureName.get(currSignatureByName.getKey()),
                    dynDispatchSupportedOverTypeArgsByRequiredReturnTypeParImpls.build()
                        .get(genTypeVariantsByRequiredAssertedTypeParImpls.getKey()),
                    contractTypeParamsAsVariantOneofsBuilder.build().values().asList()
                );
            scopedHeap.putIdentifierValue(
                String.format(
                    "%s_$VARIANT$_%s_DYNAMIC_DISPATCH_%s",
                    this.contractName,
                    Hashing.sha256().hashUnencodedChars(
                        genTypeVariantsByRequiredAssertedTypeParImpls.getKey().values().stream()
                            .map(Type::toString)
                            .collect(Collectors.joining("_"))),
                    currSignatureByName.getKey()
                ),
                this.declaredContractSignaturesByProcedureName.get(currSignatureByName.getKey())
                    .getProcedureTypeFromConcreteArgTypesAndOptionalReturnType(
                        (ImmutableList<Type>) supportedConcreteArgTypesAndOutputType.get(0),
                        (Optional<Type>) supportedConcreteArgTypesAndOutputType.get(1)
                    )
            );
          }
          dynDispatchSupportedForAtLeastOneConcreteReturnType |= isDynamicDispatchSupported;
          // Need to reset for the next iteration.
          contractTypeParamsAsVariantOneofsBuilder = ImmutableMap.builder();
          isDynamicDispatchSupported = false;
        }
        if (dynDispatchSupportedForAtLeastOneConcreteReturnType) {
          // Here I'm going to put a scoped heap entry for the general dynamic dispatch type just for the sake of having
          // something to mark used during codegen.
          scopedHeap.putIdentifierValue(
              String.format(
                  "%s_DYNAMIC_DISPATCH_%s",
                  this.contractName,
                  currSignatureByName.getKey()
              ),
              null
          );
        }
        contractProceduresSupportingDynamicDispatchOverArgsWhenGenericReturnTypeInferenceRequiredBuilder
            .put(currSignatureByName.getKey(), dynDispatchSupportedOverTypeArgsByRequiredReturnTypeParImpls.build());
      } else {
        // This contract procedure doesn't require Generic Return Type Inference. Therefore we can accept any dynamic
        // dispatch call.
        ImmutableList.Builder<Integer> contractProceduresSupportingDynamicDispatchOverArgsForCurrSignatureBuilder
            = ImmutableList.builder();
        for (int i = 0; i < this.typeParamNames.size(); i++) {
          String name = this.typeParamNames.get(i);
          ImmutableSet<Type> currTypeParamVariants = ImmutableSet.copyOf(contractGenTypeParamVariants.get(name));
          if (currTypeParamVariants.size() <= 1 ||
              currSignatureByName.getValue().requiredContextualOutputTypeAssertionTypeParamNames.contains(name)) {
            // If there's only even a single concrete type variant or if the curr type param is one that requires
            // Generic Return Type Inference, then we don't support it for dynamic dispatch.
            contractTypeParamsAsVariantOneofsBuilder.put(name, currTypeParamVariants.asList().get(0));
          } else {
            isDynamicDispatchSupported = true;
            contractTypeParamsAsVariantOneofsBuilder.put(
                name, Types.OneofType.forVariantTypes(currTypeParamVariants.asList()));
            // Make note of the fact that dynamic dispatch is supported specifically over this arg.
            contractProceduresSupportingDynamicDispatchOverArgsForCurrSignatureBuilder.add(i);
          }
        }
        contractProceduresSupportingDynamicDispatchOverArgs.putAll(
            currSignatureByName.getKey(),
            contractProceduresSupportingDynamicDispatchOverArgsForCurrSignatureBuilder.build()
        );

        if (isDynamicDispatchSupported) {
          // In this case, Dynamic Dispatch can be supported over at least one of the Contract's Type Params.
          ImmutableList<Object> supportedConcreteArgTypesAndOutputType =
              getAllSupportedDynamicDispatchConcreteContractProcedureArgTypes(
                  this.declaredContractSignaturesByProcedureName.get(currSignatureByName.getKey()),
                  contractProceduresSupportingDynamicDispatchOverArgsForCurrSignatureBuilder.build(),
                  contractTypeParamsAsVariantOneofsBuilder.build().values().asList()
              );
          scopedHeap.putIdentifierValue(
              String.format("%s_DYNAMIC_DISPATCH_%s", this.contractName, currSignatureByName.getKey()),
              this.declaredContractSignaturesByProcedureName.get(currSignatureByName.getKey())
                  .getProcedureTypeFromConcreteArgTypesAndOptionalReturnType(
                      (ImmutableList<Type>) supportedConcreteArgTypesAndOutputType.get(0),
                      (Optional<Type>) supportedConcreteArgTypesAndOutputType.get(1)
                  )
          );
        }
      }
    }
    this.contractProceduresSupportingDynamicDispatchOverArgs =
        contractProceduresSupportingDynamicDispatchOverArgs.build();
    this.contractProceduresSupportingDynamicDispatchOverArgsWhenGenericReturnTypeInferenceRequired =
        contractProceduresSupportingDynamicDispatchOverArgsWhenGenericReturnTypeInferenceRequiredBuilder.build();
  }

  // This function ensures that we're not accidentally claiming support for dynamic dispatch over [oneof<A,B>] when
  // dynamic dispatch can only actually be supported over oneof<[A], [B]>.
  //
  // Java is hot garbage and has no tuples or multiple returns so this function is actually returning
  // ([Type], Optional<Type>) representing args and optional return.
  private ImmutableList<Object> getAllSupportedDynamicDispatchConcreteContractProcedureArgTypes(
      ContractProcedureSignatureDefinitionStmt contractProcedureSignatureDefinitionStmt,
      ImmutableCollection<Integer> dynamicDispatchSupportedOverTypeParamIndices,
      ImmutableList<Type> types
  ) {
    ArrayList<ImmutableList<Object>> supportedProcedureTypeVariants =
        getAllSupportedDynamicDispatchConcreteContractProcedureTypes(
            contractProcedureSignatureDefinitionStmt,
            dynamicDispatchSupportedOverTypeParamIndices,
            types,
            new ArrayList<>(),
            0
        );

    ImmutableList.Builder<Type> resArgTypesBuilder = ImmutableList.builder();
    for (int i = 0; i < contractProcedureSignatureDefinitionStmt.resolvedArgTypes.size(); i++) {
      ImmutableSet.Builder<Type> currArgVariantsBuilder = ImmutableSet.builder();
      for (ImmutableList<Object> supportedProcedureTypesVariant : supportedProcedureTypeVariants) {
        currArgVariantsBuilder.add(((ImmutableList<Type>) supportedProcedureTypesVariant.get(0)).get(i));
      }
      ImmutableList<Type> currArgVariants = currArgVariantsBuilder.build().asList();
      if (currArgVariants.size() == 1) {
        resArgTypesBuilder.add(currArgVariants.get(0));
      } else {
        resArgTypesBuilder.add(Types.OneofType.forVariantTypes(currArgVariants));
      }
    }
    Optional<Type> resOutputTypes =
        contractProcedureSignatureDefinitionStmt.resolvedOutputType.map(
            ignored -> {
              ImmutableList<Type> supportedOutputTypeVariants =
                  supportedProcedureTypeVariants.stream().map(l -> ((Optional<Type>) l.get(1)).get())
                      .collect(ImmutableSet.toImmutableSet()).asList();
              if (supportedOutputTypeVariants.size() == 1) {
                return supportedOutputTypeVariants.get(0);
              }
              return Types.OneofType.forVariantTypes(supportedOutputTypeVariants);
            }
        );

    return ImmutableList.of(resArgTypesBuilder.build(), resOutputTypes);
  }

  private ArrayList<ImmutableList<Object>> getAllSupportedDynamicDispatchConcreteContractProcedureTypes(
      ContractProcedureSignatureDefinitionStmt contractProcedureSignatureDefinitionStmt,
      ImmutableCollection<Integer> dynamicDispatchSupportedOverTypeParamIndices,
      ImmutableList<Type> types,
      ArrayList<Type> currTypesResolvedVariants,
      int i) {
    ArrayList<ImmutableList<Object>> res = new ArrayList<>();
    if (i >= types.size()) {
      HashMap<Type, Type> typeParamsForInferenceMap = Maps.newHashMap();
      res.add(
          ImmutableList.of(
              contractProcedureSignatureDefinitionStmt.getConcreteArgTypesForConcreteContractTypeParams(
                  IntStream.range(0, i).boxed()
                      .collect(ImmutableMap.toImmutableMap(this.typeParamNames::get, currTypesResolvedVariants::get)),
                  typeParamsForInferenceMap
              ),
              contractProcedureSignatureDefinitionStmt.getConcreteOutputTypesForConcreteContractTypeParams(typeParamsForInferenceMap)
          )
      );

    } else if (dynamicDispatchSupportedOverTypeParamIndices.contains(i)
               && types.get(i).baseType().equals(BaseType.ONEOF)) {
      for (Type oneofVariant : ((Types.OneofType) types.get(i)).getVariantTypes()) {
        currTypesResolvedVariants.add(oneofVariant);
        res.addAll(
            getAllSupportedDynamicDispatchConcreteContractProcedureTypes(
                contractProcedureSignatureDefinitionStmt, dynamicDispatchSupportedOverTypeParamIndices, types, currTypesResolvedVariants,
                i + 1
            ));
        currTypesResolvedVariants.remove(currTypesResolvedVariants.size() - 1);
      }
    } else {
      currTypesResolvedVariants.add(types.get(i));
      res.addAll(
          getAllSupportedDynamicDispatchConcreteContractProcedureTypes(
              contractProcedureSignatureDefinitionStmt, dynamicDispatchSupportedOverTypeParamIndices, types, currTypesResolvedVariants,
              i + 1
          ));
      currTypesResolvedVariants.remove(currTypesResolvedVariants.size() - 1);
    }
    return res;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Technically the approach that I've followed here is actually fundamentally overlooking the fact
    //   that for contract Foo<InParamA,InParamB,OutParam> it isn't sufficient to have impls:
    //     - Foo<A1, B1, OutParam1>
    //     - Foo<A2, B2, OutParam1>
    //   So, while the ContractFunctionCall impl is correctly working around this, so there's no observable user-facing
    //   bug - Claro's currently going to be codegen'ing a dyn dispatch switch function when it is not possible that it
    //   would ever be used.
    if (this.contractProceduresSupportingDynamicDispatchOverArgs.isEmpty()
        && (this.contractProceduresSupportingDynamicDispatchOverArgsWhenGenericReturnTypeInferenceRequired.isEmpty()
            || this.contractProceduresSupportingDynamicDispatchOverArgsWhenGenericReturnTypeInferenceRequired
                .values().stream().allMatch(m -> m.isEmpty()))) {
      // Nothing to codegen, no Dynamic Dispatch supported for this contract.
      return GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
    }

    StringBuilder res = new StringBuilder("public static class ")
        .append("/*")
        .append(this.contractName)
        .append("<")
        .append(String.join(", ", this.typeParamNames))
        .append(">*/ ")
        .append(
            String.format(
                "$%s_DYNAMIC_DISPATCH_HANDLERS {\n",
                this.contractName
            ));
    this.declaredContractSignaturesByProcedureName.keySet().forEach(
        procedureName -> res.append(generateDynamicDispatchHelperForContractProcedure(procedureName, scopedHeap)));
    return GeneratedJavaSource.forStaticDefinitions(res.append("}\n"));
  }

  private StringBuilder generateDynamicDispatchHelperForContractProcedure(String procedureName, ScopedHeap scopedHeap) {
    StringBuilder res = new StringBuilder();

    // Make sure that it's even possible to do dynamic dispatch.
    if (
      // It depends fundamentally on there being args in order to accept oneofs sometimes.
        this.declaredContractSignaturesByProcedureName.get(procedureName).resolvedArgTypes.isEmpty()
        // If this is a generic procedure, then we don't bother generating this if there are no monomorphizations to
        // dispatch to (because there were no calls to this procedure).
        || (this.declaredContractSignaturesByProcedureName.get(procedureName).optionalGenericTypesList.isPresent()
            && ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName).stream().noneMatch(
            implTypeParams ->
                InternalStaticStateUtil.GenericProcedureDefinitionStmt_monomorphizationsByGenericProcedureCanonName.containsRow(
                    ContractProcedureImplementationStmt.getCanonicalProcedureName(
                        this.contractName,
                        implTypeParams.values().asList(),
                        procedureName
                    ))))) {
      return res;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Codegen Static Initialization of "vtable".
    ////////////////////////////////////////////////////////////////////////////////
    res.append("private static final ImmutableMap<ImmutableList<Object>, Integer> ")
        .append("$")
        .append(procedureName)
        .append("_vtable;\n")
        .append("static {\n")
        .append("\tImmutableMap.Builder<ImmutableList<Object>, Integer> ")
        .append("$")
        .append(procedureName)
        .append("_vtableBuilder = ImmutableMap.builder();\n");
    res.append("\t$").append(procedureName).append("_vtableBuilder\n");
    AtomicInteger monomorphizationsNumber = new AtomicInteger(0);
    Consumer<Integer> codegenVtableEntry = i1 -> {
      StringBuilder putEntryCodegen =
          new StringBuilder("\t\t.put(ImmutableList.of(")
              .append(
                  contractImplementationsByContractName.get(ContractDefinitionStmt.this.contractName)
                      .get(i1)
                      .values()
                      .stream()
                      .map(t -> {
                        if (t instanceof ConcreteType) {
                          return t.getJavaSourceType() + ".class";
                        }
                        return t.getJavaSourceClaroType();
                      })
                      .collect(Collectors.joining(", ")));
      if (this.declaredContractSignaturesByProcedureName.get(procedureName).optionalGenericTypesList.isPresent()) {
        ImmutableList<String> genericTypeParamNames =
            this.declaredContractSignaturesByProcedureName.get(procedureName).optionalGenericTypesList.get();
        InternalStaticStateUtil.GenericProcedureDefinitionStmt_monomorphizationsByGenericProcedureCanonName.row(
                ContractProcedureImplementationStmt.getCanonicalProcedureName(
                    this.contractName,
                    ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName).get(i1)
                        .values().asList(),
                    procedureName
                ))
            .keySet().forEach(
                monomorphizationConcreteTypes -> {
                  res.append(putEntryCodegen)
                      .append(
                          monomorphizationConcreteTypes.values().stream()
                              .map(concreteType -> {
                                if (concreteType instanceof ConcreteType) {
                                  return concreteType.baseType().nativeJavaSourceImplClazz.get().getSimpleName() + ".class";
                                }
                                return concreteType.getJavaSourceClaroType();
                              })
                              .collect(Collectors.joining("", ", ", "")));
                  res.append("), ").append(monomorphizationsNumber.getAndIncrement()).append(")\n");
                }
            );
      } else {
        res.append(putEntryCodegen).append("), ").append(i1).append(")");
      }
    };
    for (int i = 0; i < contractImplementationsByContractName.get(this.contractName).size() - 1; i++) {
      codegenVtableEntry.accept(i);
      res.append("\n");
    }
    codegenVtableEntry.accept(contractImplementationsByContractName.get(this.contractName).size() - 1);
    res.append(";\n\t")
        .append("$")
        .append(procedureName)
        .append("_vtable = $")
        .append(procedureName)
        .append("_vtableBuilder.build();\n");
    ////////////////////////////////////////////////////////////////////////////////
    // Finish Static Initialization of "vtable".
    ////////////////////////////////////////////////////////////////////////////////
    res.append("}\n");

    ////////////////////////////////////////////////////////////////////////////////
    // Codegen signature.
    ////////////////////////////////////////////////////////////////////////////////
    res.append("static ");
    if (this.declaredContractSignaturesByProcedureName.get(procedureName).resolvedOutputType.isPresent()) {
      if (this.declaredContractSignaturesByProcedureName.get(procedureName).resolvedOutputType.get()
          .getGenericContractTypeParamsReferencedByType().isEmpty()) {
        res.append(
                this.declaredContractSignaturesByProcedureName.get(procedureName).resolvedOutputType.get()
                    .toType().getJavaSourceType())
            .append(' ');
      } else {
        res.append("<O> O ");
      }
    } else {
      res.append("void ");
    }
    res.append(this.contractName).append("_DYNAMIC_DISPATCH_").append(procedureName).append("(\n");

    ////////////////////////////////////////////////////////////////////////////////
    // Codegen args.
    ////////////////////////////////////////////////////////////////////////////////
    ImmutableSet<String> supportedDynamicDispatchTypeParamNames =
        ImmutableSet.copyOf(this.contractProceduresSupportingDynamicDispatchOverArgs.get(procedureName))
            .stream()
            .map(this.typeParamNames::get)
            .collect(ImmutableSet.toImmutableSet());
    ImmutableList<ContractProcedureSignatureDefinitionStmt.GenericSignatureType> currProcedureGenArgTypes =
        this.declaredContractSignaturesByProcedureName.get(procedureName).resolvedArgTypes;
    ImmutableList<String> currProcedureArgNames =
        this.declaredContractSignaturesByProcedureName.get(procedureName)
            .optionalArgTypeProvidersByNameMap.get().keySet().asList();
    Consumer<Integer> codegenArg = i1 -> {
      // This arg references some generic type. It may be referencing either a Contract Type Param (which will end up
      // being used for contract impl dyn dispatch), AND/OR a true Generic Type Param (which will end up being used for
      // generic function monomorphization dyn dispatch).
      if (!currProcedureGenArgTypes.get(i1).getGenericContractTypeParamsReferencedByType().isEmpty()) {
        res.append("\t/*DYN DISPATCH OVER THIS ARG*/Object ")
            .append(currProcedureArgNames.get(i1));
      } else {
        res.append("\t")
            .append(currProcedureGenArgTypes.get(i1).toType().getJavaSourceType())
            .append(" ")
            .append(currProcedureArgNames.get(i1));
      }
    };
    for (int i = 0; i < currProcedureGenArgTypes.size() - 1; i++) {
      codegenArg.accept(i);
      res.append(",\n");
    }
    codegenArg.accept(currProcedureGenArgTypes.size() - 1);

    // If Generic Return Type Inference is required over this function call, then we need the call-site to statically
    // provide the vtable keys for those return-type-only contract type params.
    if (!this.declaredContractSignaturesByProcedureName.get(procedureName)
        .requiredContextualOutputTypeAssertionTypeParamNames.isEmpty()) {
      for (String requiredAssertedOutputType :
          this.declaredContractSignaturesByProcedureName.get(procedureName)
              .requiredContextualOutputTypeAssertionTypeParamNames) {
        res.append(",\n\t/*REQUIRED CONTEXTUALLY ASSERTED OUTPUT TYPE*/Object $")
            .append(requiredAssertedOutputType)
            .append("_vtableKey");
      }
    }
    // If the Contract Procedure is also Generic, then, in order to know which monomorphization to dispatch to, I'll
    // need to have the call-site statically provide the monomorphization-vtable keys for the Generic Type Params.
    this.declaredContractSignaturesByProcedureName.get(procedureName).optionalGenericTypesList.ifPresent(
        genericTypeList -> genericTypeList.forEach(
            g -> res.append(",\n\t/*STATICALLY INFERRED CONCRETE GENERIC TYPE*/Object $")
                .append(g)
                .append("_monomorphizations_vtableKey")
        ));

    ////////////////////////////////////////////////////////////////////////////////
    // Finish codegen args.
    ////////////////////////////////////////////////////////////////////////////////
    res.append(") {\n");

    ////////////////////////////////////////////////////////////////////////////////
    // Codegen body.
    ////////////////////////////////////////////////////////////////////////////////

    // Here we'll use structural type inference to codegen the sequence of type casts and checks to determine the
    // runtime concrete contract type params.
    HashMap<Type, Type> contractTypeParams = Maps.newHashMap();
    this.typeParamNames.forEach(n -> {
      Types.$GenericTypeParam g = Types.$GenericTypeParam.forTypeParamName(n);
      contractTypeParams.put(g, g);
    });
    // These will get filled in by the structural type matching.
    HashMap<Type, ImmutableList<ImmutableList<StringBuilder>>> typeCheckingCodegenForDynamicDispatch =
        Maps.newHashMap();
    Stack<ImmutableList<StringBuilder>> typeCheckingCodegenPath = new Stack<>();
    try {
      ContractProcedureSignatureDefinitionStmt
          signature = this.declaredContractSignaturesByProcedureName.get(procedureName);
      for (int argIndexForConcreteTypeInference :
          signature.contextualOutputTypeAssertionRequired
          ? signature.inferContractImplTypesFromArgsWhenContextualOutputTypeAsserted.get()
          : signature.inferContractImplTypesFromArgs) {
        Type signatureArgType = signature.resolvedArgTypes.get(argIndexForConcreteTypeInference).toType();
        Set<Type> alreadyCodegendtypes = ImmutableSet.copyOf(typeCheckingCodegenForDynamicDispatch.keySet());
        StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
            contractTypeParams,
            signatureArgType,
            signatureArgType,
            false,
            Optional.of(typeCheckingCodegenForDynamicDispatch),
            Optional.of(typeCheckingCodegenPath),
            Optional.empty(),
            /*withinNestedCollectionType=*/false
        );
        for (Type contractTypeParamName : Sets.difference(typeCheckingCodegenForDynamicDispatch.keySet(), alreadyCodegendtypes)) {
          String argAtInd = currProcedureArgNames.get(argIndexForConcreteTypeInference);
          res.append("\tObject $").append(contractTypeParamName).append("_vtableKey = ");
          if (typeCheckingCodegenForDynamicDispatch.get(contractTypeParamName).isEmpty()) {
            res.append(argAtInd).append(" instanceof ClaroTypeImplementation ");
            res.append("? ((ClaroTypeImplementation) ").append(argAtInd).append(").getClaroType() ");
            res.append(": ").append(argAtInd).append(".getClass()");
          } else {
            // Need to dig into the structural type.
            for (ImmutableList<StringBuilder> s : typeCheckingCodegenForDynamicDispatch.get(contractTypeParamName)
                .reverse()) {
              res.append(s.get(0));
            }
            res.append("((ClaroTypeImplementation) ").append(argAtInd).append(").getClaroType()");
            for (ImmutableList<StringBuilder> s : typeCheckingCodegenForDynamicDispatch.get(contractTypeParamName)) {
              res.append(s.get(1));
            }
            res.append(";\n\t$")
                .append(contractTypeParamName)
                .append("_vtableKey = $")
                .append(contractTypeParamName)
                .append("_vtableKey instanceof ConcreteType ");
            res.append("? ((ConcreteType) $")
                .append(contractTypeParamName)
                .append("_vtableKey).baseType().nativeJavaSourceImplClazz.get() ");
            res.append(": $").append(contractTypeParamName).append("_vtableKey");
          }
          res.append(";\n");
        }
      }
    } catch (ClaroTypeException e) {
      throw new IllegalStateException("Internal Compiler Error: IMPOSSIBLE!");
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Codegen switch over dynamically resolved contract impl type param types.
    ////////////////////////////////////////////////////////////////////////////////

    res.append("\tswitch($").append(procedureName).append("_vtable.get(ImmutableList.of(");
    String contractTypeParamKeysCodegen =
        IntStream.range(0, this.typeParamNames.size())
            .mapToObj(n -> {
              if (this.contractProceduresSupportingDynamicDispatchOverArgs.get(procedureName).contains(n)
                  || this.declaredContractSignaturesByProcedureName.get(procedureName)
                      .requiredContextualOutputTypeAssertionTypeParamNames
                      .contains(this.typeParamNames.get(n))
                  ||
                  Optional.ofNullable(
                          this.contractProceduresSupportingDynamicDispatchOverArgsWhenGenericReturnTypeInferenceRequired
                              .get(procedureName))
                      .map(m -> m.values().stream().anyMatch(i -> n == i))
                      .orElse(false)) {
                return String.format("$%s_vtableKey", this.typeParamNames.get(n));
              }
              Type onlyConcreteTypeForCurrTypeParam =
                  ContractDefinitionStmt.contractImplementationsByContractName
                      .get(this.contractName).get(0).get(this.typeParamNames.get(n));
              // This arg isn't supported for dynamic dispatch meaning that there's only a single concrete type
              // used for this type param, so just grab the first implementation's entry for this type param.
              return onlyConcreteTypeForCurrTypeParam instanceof ConcreteType
                     ? onlyConcreteTypeForCurrTypeParam.baseType().nativeJavaSourceImplClazz.get()
                           .getSimpleName() + ".class"
                     : onlyConcreteTypeForCurrTypeParam.getJavaSourceClaroType();
            })
            .collect(Collectors.joining(", "));
    res.append(contractTypeParamKeysCodegen);
    this.declaredContractSignaturesByProcedureName.get(procedureName).optionalGenericTypesList.ifPresent(
        genericTypeParamNames -> genericTypeParamNames.stream().forEach(
            n -> res.append(String.format(", $%s_monomorphizations_vtableKey", n))
        )
    );
    res.append("))) {\n");
    final AtomicInteger monomorphizationNumber = new AtomicInteger();
    for (int i = 0;
         i < ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName).size();
         i++) {
      String contractImplCodegenClassName = (String) scopedHeap.getIdentifierValue(
          ContractImplementationStmt.getContractTypeString(
              this.contractName,
              ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName)
                  .get(i)
                  .values()
                  .stream()
                  .map(Type::toString)
                  .collect(ImmutableList.toImmutableList())
          )
      );
      if (this.declaredContractSignaturesByProcedureName.get(procedureName).optionalGenericTypesList.isPresent()) {
        int finalI = i;
        InternalStaticStateUtil.GenericProcedureDefinitionStmt_monomorphizationsByGenericProcedureCanonName.row(
                ContractProcedureImplementationStmt.getCanonicalProcedureName(
                    this.contractName,
                    ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName).get(i)
                        .values().asList(),
                    procedureName
                ))
            .forEach(
                (monomorphizationTypes, canonicalizedName) -> {
                  res.append("\t\tcase ").append(monomorphizationNumber.getAndIncrement()).append(": ");
                  res.append("/*CONCRETE CONTRACT TYPE PARAMS: ")
                      .append(ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName)
                                  .get(finalI));
                  res.append(" - CONCRETE GENERIC TYPE PARAMS: ").append(monomorphizationTypes).append("*/\n");
                  res.append("\t\t\t");
                  if (this.declaredContractSignaturesByProcedureName.get(procedureName).resolvedOutputType.isPresent()) {
                    if (this.declaredContractSignaturesByProcedureName.get(procedureName).resolvedOutputType.get()
                        .getGenericContractTypeParamsReferencedByType().isEmpty()) {
                      res.append("return ");
                    } else {
                      res.append("return (O) ");
                    }
                  }
                  res.append(
                      String.format(
                          "%s.%s__%s.apply(%s);\n",
                          contractImplCodegenClassName,
                          procedureName,
                          Hashing.sha256().hashUnencodedChars(
                              canonicalizedName
                          ),
                          String.join(", ", currProcedureArgNames)
                      )
                  );
                });
      } else {
        res.append("\t\tcase ").append(i).append(": ").append("/*")
            .append(ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName)
                        .get(i).toString())
            .append("*/\n")
            .append("\t\t\t");
        if (this.declaredContractSignaturesByProcedureName.get(procedureName).resolvedOutputType.isPresent()) {
          if (this.declaredContractSignaturesByProcedureName.get(procedureName).resolvedOutputType.get()
              .getGenericContractTypeParamsReferencedByType().isEmpty()) {
            res.append("return ");
          } else {
            res.append("return (O) ");
          }
        }
        res.append(
            String.format(
                "%s.%s__%s.apply(%s);\n",
                contractImplCodegenClassName,
                procedureName,
                Hashing.sha256().hashUnencodedChars(
                    ContractProcedureImplementationStmt.getCanonicalProcedureName(
                        this.contractName,
                        ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName)
                            .get(i).values().asList(),
                        procedureName
                    )).toString(),
                String.join(", ", currProcedureArgNames)
            )
        );
        if (!this.declaredContractSignaturesByProcedureName.get(procedureName).resolvedOutputType.isPresent()) {
          res.append("\t\t\treturn;\n");
        }
      }
    }
    res.append("\t\tdefault:\n\t\t\tthrow new IllegalStateException(\"IMPOSSIBLE!!!\");");
    res.append("\n\t}\n");

    ////////////////////////////////////////////////////////////////////////////////
    // Finish codegen body.
    ////////////////////////////////////////////////////////////////////////////////
    return res.append("\n}\n");
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return null;
  }
}
