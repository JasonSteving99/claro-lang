package com.claro.intermediate_representation.statements.contracts;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;

import java.util.function.Function;

public class ContractImplementationStmt extends Stmt {
  private final String contractName;
  private final String implementationName;
  private final ImmutableList<TypeProvider> concreteImplementationTypeParamTypeProviders;
  private final ImmutableList<ContractProcedureImplementationStmt> contractProcedureImplementationStmts;

  private boolean alreadyAssertedTypes = false;
  private ImmutableMap<String, Type> concreteImplementationTypeParams;
  private String canonicalImplementationName;
  private ContractDefinitionStmt contractDefinitionStmt;
  private ImmutableList<String> concreteTypeStrings;
  static GeneratedJavaSource dependencyGenericProcedureDefCodegenJavaSource =
      GeneratedJavaSource.forJavaSourceBody(new StringBuilder());

  public ContractImplementationStmt(
      String contractName,
      String implementationName,
      ImmutableList<TypeProvider> concreteImplementationTypeParamTypeProviders,
      ImmutableList<ContractProcedureImplementationStmt> contractProcedureImplementationStmts) {
    super(ImmutableList.of());
    this.contractName = contractName;
    this.implementationName = implementationName;
    this.concreteImplementationTypeParamTypeProviders = concreteImplementationTypeParamTypeProviders;
    this.contractProcedureImplementationStmts = contractProcedureImplementationStmts;
  }

  public void registerProcedureTypeProviders(ScopedHeap scopedHeap) {
    this.contractDefinitionStmt =
        (ContractDefinitionStmt) scopedHeap.getIdentifierValue(this.contractName);

    // Validate that the types in the implementation actually exist, and note them down.
    ImmutableMap.Builder<String, Type> concreteImplementationTypeParamsBuilder = ImmutableMap.builder();
    for (int i = 0; i < this.concreteImplementationTypeParamTypeProviders.size(); i++) {
      concreteImplementationTypeParamsBuilder.put(
          this.contractDefinitionStmt.typeParamNames.get(i),
          this.concreteImplementationTypeParamTypeProviders.get(i).resolveType(scopedHeap)
      );
    }
    this.concreteImplementationTypeParams = concreteImplementationTypeParamsBuilder.build();

    // Register this Contract Implementation for these types so that the implemented procedures can
    // be de-referenced through the contract impl by type inference.
    this.concreteTypeStrings = this.concreteImplementationTypeParams.values().stream()
        .map(Type::toString)
        .collect(ImmutableList.toImmutableList());
    this.canonicalImplementationName = getContractTypeString(this.contractName, concreteTypeStrings);

    // Now validate that this isn't a duplicate of another existing implementation of this contract.
    if (scopedHeap.isIdentifierDeclared(this.canonicalImplementationName)) {
      throw new RuntimeException(
          ClaroTypeException.forDuplicateContractImplementation(
              getContractTypeString(this.implementationName, concreteTypeStrings),
              getContractTypeString((String) scopedHeap.getIdentifierValue(this.canonicalImplementationName), concreteTypeStrings)
          ));
    }

    scopedHeap.putIdentifierValue(
        this.canonicalImplementationName,
        Types.$ContractImplementation.forContractNameAndConcreteTypeParams(
            this.contractName, this.concreteImplementationTypeParams.values().asList()),
        this.implementationName
    );

    // Now register the actual procedure defs in this contract implementation.
    for (ContractProcedureImplementationStmt implementationStmt : this.contractProcedureImplementationStmts) {
      implementationStmt.registerProcedureTypeProvider(
          scopedHeap, this.contractName, this.concreteImplementationTypeParams.values().asList());
    }
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (!alreadyAssertedTypes) {
      alreadyAssertedTypes = true;

      // First thing, check that the named contract even exists.
      if (!scopedHeap.isIdentifierDeclared(this.contractName)) {
        throw ClaroTypeException.forImplementationOfUnknownContract(this.contractName, this.implementationName);
      }
      // Make sure that the implementation name isn't already in use.
      if (scopedHeap.isIdentifierDeclared(this.implementationName)) {
        throw ClaroTypeException.forUnexpectedIdentifierRedeclaration(this.implementationName);
      }

      // Check that there are the correct number of type params.
      if (this.contractDefinitionStmt.typeParamNames.size() != this.concreteImplementationTypeParams.size()) {
        throw ClaroTypeException.forContractImplementationWithWrongNumberOfTypeParams(
            getContractTypeString(this.implementationName, concreteTypeStrings),
            getContractTypeString(this.contractName, this.contractDefinitionStmt.typeParamNames)
        );
      }

      // Now validate that we have definitions for all of the required signatures and no more.
      ImmutableSet<String> implementedProcedureNamesSet =
          this.contractProcedureImplementationStmts.stream()
              .map(contractProcedureImplementationStmt ->
                       contractProcedureImplementationStmt.procedureName)
              .collect(ImmutableSet.toImmutableSet());
      ImmutableSet<String> contractProcedureNamesSet =
          this.contractDefinitionStmt.declaredContractSignaturesByProcedureName.keySet();
      if (!implementedProcedureNamesSet.equals(contractProcedureNamesSet)) {
        // We shouldn't be missing any procedure definitions.
        Sets.SetView<String> missingContractProcedures =
            Sets.difference(contractProcedureNamesSet, implementedProcedureNamesSet);
        if (!missingContractProcedures.isEmpty()) {
          throw ClaroTypeException.forContractImplementationMissingRequiredProcedureDefinitions(
              getContractTypeString(this.implementationName, this.concreteImplementationTypeParams.values()
                  .stream()
                  .map(Type::toString)
                  .collect(ImmutableList.toImmutableList())),
              missingContractProcedures
          );
        }
        // There also shouldn't be any extra procedure definitions.
        Sets.SetView<String> extraContractImplProcedures =
            Sets.difference(implementedProcedureNamesSet, contractProcedureNamesSet);
        if (!extraContractImplProcedures.isEmpty()) {
          throw ClaroTypeException.forContractImplementationWithExtraProcedureDefinitions(
              getContractTypeString(this.implementationName, this.concreteImplementationTypeParams.values()
                  .stream()
                  .map(Type::toString)
                  .collect(ImmutableList.toImmutableList())),
              extraContractImplProcedures
          );
        }
      }

      // Defer to type validation. Which will validate the ProcedureDefinitionStmt itself after validating that
      // the required signature is followed.
      for (ContractProcedureImplementationStmt implementationStmt : this.contractProcedureImplementationStmts) {
        implementationStmt.assertExpectedExprTypes(
            scopedHeap, canonicalImplementationName, this.contractDefinitionStmt, this.concreteImplementationTypeParams);
      }

      // Register this implementation in the ContractDefinitionStmt so that there's an easy way to go from Contract
      // name to implementation types w/o having to scan the whole scoped heap.
      this.contractDefinitionStmt.contractImplementationsByContractName.get(this.contractName)
          .add(this.concreteImplementationTypeParams);

      // Finally, add this implementation to the scoped heap so that it can't be re-implemented.
      scopedHeap.putIdentifierValue(
          this.canonicalImplementationName,
          Types.$ContractImplementation.forContractNameAndConcreteTypeParams(
              this.contractName, this.concreteImplementationTypeParams.values().asList()),
          this.implementationName
      );
      scopedHeap.markIdentifierUsed(this.canonicalImplementationName);
      scopedHeap.putIdentifierValue(
          this.implementationName,
          Types.$ContractImplementation.forContractNameAndConcreteTypeParams(
              this.contractName, this.concreteImplementationTypeParams.values().asList()),
          null
      );
      scopedHeap.markIdentifierUsed(this.implementationName);
    }
  }

  public static String getContractTypeString(String contractName, ImmutableList<String> typeParams) {
    return String.format(
        "%s<%s>",
        contractName,
        String.join(", ", typeParams)
    );
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder res =
        new StringBuilder("  public static final class ")
            .append(this.implementationName)
            .append(" {\n");

    // In order to avoid using names that are way too long for Java, we're going to hash all names within this
    // contract implementation. I won't worry about maintaining the old names here, because these variables should
    // never be referenced anymore after codegen.
    Function<Integer, String> getHashedContractProcedureImplName =
        i ->
            String.format(
                "%s__%s",
                this.contractProcedureImplementationStmts.get(i).procedureName,
                Hashing.sha256()
                    .hashUnencodedChars(this.contractProcedureImplementationStmts.get(i).canonicalProcedureName)
                    .toString()
            );
    this.contractProcedureImplementationStmts.get(0)
        .updateProcedureDefName(getHashedContractProcedureImplName.apply(0));
    Function<Integer, GeneratedJavaSource> noteOriginalType =
        i ->
            GeneratedJavaSource.forStaticDefinitions(new StringBuilder(
                String.format(
                    "/*%s*/\n",
                    this.contractProcedureImplementationStmts.get(i).resolvedProcedureType.toString()
                )));
    GeneratedJavaSource implementationProcedureDefinitions =
        noteOriginalType.apply(0).createMerged(
            this.contractProcedureImplementationStmts.get(0).generateJavaSourceOutput(scopedHeap));
    int i = 1;
    for (ContractProcedureImplementationStmt implementationStmt :
        this.contractProcedureImplementationStmts.subList(1, this.contractProcedureImplementationStmts.size())) {
      implementationStmt.updateProcedureDefName(getHashedContractProcedureImplName.apply(i));
      implementationProcedureDefinitions =
          noteOriginalType.apply(i).createMerged(
              implementationProcedureDefinitions.createMerged(implementationStmt.generateJavaSourceOutput(scopedHeap)));
      ++i;
    }

    res.append("// Static preamble statements first thing.\n")
        .append(implementationProcedureDefinitions.optionalStaticPreambleStmts().orElse(new StringBuilder()))
        .append("\n\n")
        .append("// Now the static definitions.\n")
        .append(implementationProcedureDefinitions.optionalStaticDefinitions().orElse(new StringBuilder()))
        .append("}");

    // Because it's possible that some contract procedure impls may have deferred to a Generic procedure, then it's
    // possible that there's some generic procedure def that needs to be collected and generated *outside* the
    // class being generated for this contract impl.
    GeneratedJavaSource codegenRes =
        ContractImplementationStmt.dependencyGenericProcedureDefCodegenJavaSource.
            createMerged(GeneratedJavaSource.create(new StringBuilder(), res, new StringBuilder()));
    // Already used, drop the reference to the dependent generic codegens.
    ContractImplementationStmt.dependencyGenericProcedureDefCodegenJavaSource =
        GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
    return codegenRes;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return null;
  }
}
