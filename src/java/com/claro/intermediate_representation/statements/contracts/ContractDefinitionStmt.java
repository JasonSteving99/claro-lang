package com.claro.intermediate_representation.statements.contracts;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.procedures.functions.StructuralConcreteGenericTypeValidationUtil;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.ConcreteType;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.google.common.hash.Hashing;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ContractDefinitionStmt extends Stmt {

  private final String contractName;

  private boolean alreadyAssertedTypes = false;
  public final ImmutableList<String> typeParamNames;
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
                    .getExpectedProcedureTypeForConcreteTypeParams(contractTypeParamsAsVariantOneofsBuilder.build())
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
            contractProceduresSupportingDynamicDispatchOverArgs.put(currSignatureByName.getKey(), i);
          }
        }

        if (isDynamicDispatchSupported) {
          // In this case, Dynamic Dispatch can be supported over at least one of the Contract's Type Params.
          scopedHeap.putIdentifierValue(
              String.format("%s_DYNAMIC_DISPATCH_%s", this.contractName, currSignatureByName.getKey()),
              this.declaredContractSignaturesByProcedureName.get(currSignatureByName.getKey())
                  .getExpectedProcedureTypeForConcreteTypeParams(contractTypeParamsAsVariantOneofsBuilder.build())
          );
        }
      }
    }
    this.contractProceduresSupportingDynamicDispatchOverArgs =
        contractProceduresSupportingDynamicDispatchOverArgs.build();
    this.contractProceduresSupportingDynamicDispatchOverArgsWhenGenericReturnTypeInferenceRequired =
        contractProceduresSupportingDynamicDispatchOverArgsWhenGenericReturnTypeInferenceRequiredBuilder.build();
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

    // Make sure that it's even possible to do dynamic dispatch. It depends fundamentally on there being args in order
    // to accept oneofs sometimes.
    if (this.declaredContractSignaturesByProcedureName.get(procedureName).resolvedArgTypes.isEmpty()
        // TODO(steving) SUPPORT DYNAMIC DISPATCH OVER GENERIC CONTRACT PROCEDURES!
        || this.declaredContractSignaturesByProcedureName.get(procedureName).optionalGenericTypesList.isPresent()) {
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
    Consumer<Integer> codegenVtableEntry = i1 -> res.append(
        contractImplementationsByContractName.get(ContractDefinitionStmt.this.contractName).get(i1).values().stream()
            .map(t -> {
              if (t instanceof ConcreteType) {
                return t.getJavaSourceType() + ".class";
              }
              return t.getJavaSourceClaroType();
            })
            .collect(Collectors.joining(", ", "\t\t.put(ImmutableList.of(", "), " + i1 + ")")));
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
    ImmutableList.Builder<String> argNamesUsedForDynamicDispatch = ImmutableList.builder();
    Consumer<Integer> codegenArg = i1 -> {
      if (!currProcedureGenArgTypes.get(i1).getGenericContractTypeParamsReferencedByType().isEmpty()) {
        // This arg references some generic type.
        if (currProcedureGenArgTypes.get(i1)
            .getGenericContractTypeParamsReferencedByType()
            .stream()
            .anyMatch(
                g -> supportedDynamicDispatchTypeParamNames.contains(g.getTypeParamName())
                     || ContractDefinitionStmt.this.typeParamNames.contains(g.getTypeParamName()))) {
          res.append("\t/*DYN DISPATCH OVER THIS ARG*/Object ")
              .append(currProcedureArgNames.get(i1));
          argNamesUsedForDynamicDispatch.add(currProcedureArgNames.get(i1));
        } else {
          // TODO(steving) NEED TO HANDLE GENERIC CONTRACT PROCEDURES.
          throw new IllegalStateException("Internal Compiler Error: CLARO DOESN'T YET SUPPORT DYNAMIC DISPATCH OVER GENERIC CONTRACT PROCEDURES!");
        }
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
            Optional.of(typeCheckingCodegenPath)
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

    res.append("\tswitch($").append(procedureName).append("_vtable.get(ImmutableList.of(")
        .append(IntStream.range(0, this.typeParamNames.size())
                    .mapToObj(n -> {
                      if (this.contractProceduresSupportingDynamicDispatchOverArgs.get(procedureName).contains(n)
                          || this.declaredContractSignaturesByProcedureName.get(procedureName)
                              .requiredContextualOutputTypeAssertionTypeParamNames
                              .contains(this.typeParamNames.get(n))
                          ||
                          this.contractProceduresSupportingDynamicDispatchOverArgsWhenGenericReturnTypeInferenceRequired
                              .get(procedureName).values().stream().anyMatch(i -> n == i)) {
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
                    .collect(Collectors.joining(", ")))
        .append("))) {\n");
    for (int i = 0;
         i < ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName).size();
         i++) {
      res.append("\t\tcase ").append(i).append(": ").append("/*")
          .append(ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName).get(i).toString())
          .append("*/\n");
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
