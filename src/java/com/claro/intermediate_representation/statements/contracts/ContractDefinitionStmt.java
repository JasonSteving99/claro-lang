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
    for (Map.Entry<String, ContractProcedureSignatureDefinitionStmt> currSignatureByName :
        this.declaredContractSignaturesByProcedureName.entrySet()) {
      Multimap<String, Type> contractGenTypeParamVariants = HashMultimap.create();
      for (Map<String, Type> typeImplMapping : ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName)) {
        typeImplMapping.forEach(contractGenTypeParamVariants::put);
      }
      ImmutableMap.Builder<String, Type> contractTypeParamsAsVariantOneofsBuilder = ImmutableMap.builder();

      boolean isDynamicDispatchSupported = false;
      int i = 0;
      for (String name : contractGenTypeParamVariants.keySet()) {
        ImmutableSet<Type> currTypeParamVariants = ImmutableSet.copyOf(contractGenTypeParamVariants.get(name));
        isDynamicDispatchSupported |= currTypeParamVariants.size() > 1;
        contractTypeParamsAsVariantOneofsBuilder.put(
            name,
            currTypeParamVariants.size() > 1
            ? Types.OneofType.forVariantTypes(currTypeParamVariants.asList())
            : currTypeParamVariants.asList().get(0)
        );
        // Make note of the fact that dynamic dispatch is supported specifically over this arg.
        if (currTypeParamVariants.size() > 1) {
          contractProceduresSupportingDynamicDispatchOverArgs.put(currSignatureByName.getKey(), i);
        }
        i++;
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
    this.contractProceduresSupportingDynamicDispatchOverArgs =
        contractProceduresSupportingDynamicDispatchOverArgs.build();
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // TODO(steving): IMPLEMENT DYNAMIC DISPATCH OVER CONTRACTS
    if (this.contractProceduresSupportingDynamicDispatchOverArgs.isEmpty()) {
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
                              .contains(this.typeParamNames.get(n))) {
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
