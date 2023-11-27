package com.claro.intermediate_representation.statements.contracts;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ContractImplementationStmt extends Stmt {
  private final String contractName;
  private String implementationName;
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
      ImmutableList<TypeProvider> concreteImplementationTypeParamTypeProviders,
      ImmutableList<ContractProcedureImplementationStmt> contractProcedureImplementationStmts) {
    super(ImmutableList.of());
    this.contractName = contractName;
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
    this.canonicalImplementationName = getContractTypeString(this.contractName, this.concreteTypeStrings);
    this.implementationName =
        // Not encoding the full contract name into the generated implementation name, b/c Java codegen was running into
        // name too long errors for the generated class by this name (specifically was a problem for contracts defined
        // in dep modules where the contract name was some variant of `ContractName$some$long$path$to$dep$module`.
        String.format(
            "ContractImpl__%s",
            Hashing.sha256().hashUnencodedChars(this.contractName + this.canonicalImplementationName)
        );

    // Now validate that this isn't a duplicate of another existing implementation of this contract.
    if (scopedHeap.isIdentifierDeclared(this.canonicalImplementationName)) {
      throw new RuntimeException(
          ClaroTypeException.forDuplicateContractImplementation(
              getContractTypeString(this.implementationName, this.concreteTypeStrings)));
    }
    // Additionally, if this contract definition has any implied types, then we need to validate that this contract
    // hasn't already implemented over these unconstrained type params.
    if (contractDefinitionStmt.impliedTypeParamNames.size() > 0) {
      int firstImpliedTypeInd =
          contractDefinitionStmt.typeParamNames.indexOf(contractDefinitionStmt.impliedTypeParamNames.asList().get(0));
      Optional<ImmutableList<Type>> existingImplForUnconstrainedTypeParams =
          ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName).stream()
              .filter(implConcreteParams ->
                          implConcreteParams.values().asList().subList(0, firstImpliedTypeInd)
                              .equals(this.concreteImplementationTypeParams.values()
                                          .asList()
                                          .subList(0, firstImpliedTypeInd)))
              .map(m -> m.values().asList())
              .findFirst();
      if (existingImplForUnconstrainedTypeParams.isPresent()) {
        throw new RuntimeException(
            ClaroTypeException.forContractImplementationViolatingImpliedTypesConstraint(
                this.contractName,
                this.contractDefinitionStmt.typeParamNames.subList(0, firstImpliedTypeInd),
                this.contractDefinitionStmt.impliedTypeParamNames,
                getContractTypeString(this.contractName, this.concreteTypeStrings),
                getContractTypeString(
                    this.contractName,
                    existingImplForUnconstrainedTypeParams.get()
                        .stream()
                        .map(Type::toString)
                        .collect(Collectors.toList())
                )
            ));
      }
    }

    scopedHeap.putIdentifierValue(
        this.canonicalImplementationName,
        Types.$ContractImplementation.forContractNameAndConcreteTypeParams(
            this.contractName,
            Optional.ofNullable(ScopedHeap.currProgramDepModules.rowMap().get("$THIS_MODULE$"))
                .map(m -> m.values().stream().findFirst().get().getUniqueModuleName()),
            this.concreteImplementationTypeParams.values().asList()
        ),
        this.implementationName
    );

    // Now register the actual procedure defs in this contract implementation.
    for (ContractProcedureImplementationStmt implementationStmt : this.contractProcedureImplementationStmts) {
      implementationStmt.registerProcedureTypeProvider(
          scopedHeap, this.contractName, this.concreteImplementationTypeParams.values().asList());
    }

    // Register this implementation in the ContractDefinitionStmt so that there's an easy way to go from Contract
    // name to implementation types w/o having to scan the whole scoped heap.
    ContractDefinitionStmt.contractImplementationsByContractName.get(this.contractName)
        .add(this.concreteImplementationTypeParams);
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

      // Now one final validation, you're only allowed to implement contracts over types that have been defined in the
      // current compilation unit. Contracts are also implementable over *ANY* arbitrary type no matter what compilation
      // unit it was defined in, within the same compilation unit that the contract itself was defined in. This is the
      // case because anyone that would be attempting to make use of the contract in any way would first need a dep on
      // that module, so it would be possible to statically rule out the duplicate implementation of any contract for
      // the same type(s) more than once. *The one exception to this rule* is that, for the sake of convenience only,
      // the top-level claro_binary() compilation unit may define whatever contract impls that it sees fit since the
      // application of this rule could not possibly result in allowing duplication of the same contract implementation.
      String currCompilationUnitDisambiguator = ScopedHeap.getDefiningModuleDisambiguator(Optional.empty());
      if (!(((Types.$Contract) scopedHeap.getValidatedIdentifierType(this.contractName))
                .getDefiningModuleDisambiguator().equals(currCompilationUnitDisambiguator)
            || currCompilationUnitDisambiguator.isEmpty())) {
        ImmutableList<Type> implTypeParamsDefinedOutsideCurrentCompilationUnit =
            this.concreteImplementationTypeParams.values().stream()
                .filter(t -> !((t.baseType().equals(BaseType.USER_DEFINED_TYPE)
                                && ((Types.UserDefinedType) t).getDefiningModuleDisambiguator()
                                    .equals(currCompilationUnitDisambiguator))
                               || (t.baseType().equals(BaseType.ATOM) &&
                                   ((Types.AtomType) t).getDefiningModuleDisambiguator()
                                       .equals(currCompilationUnitDisambiguator))))
                .collect(ImmutableList.toImmutableList());
        if (!implTypeParamsDefinedOutsideCurrentCompilationUnit.isEmpty()) {
          throw ClaroTypeException.forIllegalContractImplOverTypesNotDefinedInCurrentCompilationUnit(
              getContractTypeString(
                  this.implementationName,
                  this.concreteImplementationTypeParams.values()
                      .stream()
                      .map(Type::toString)
                      .collect(ImmutableList.toImmutableList())
              ),
              implTypeParamsDefinedOutsideCurrentCompilationUnit
          );
        }
      }

      // Defer to type validation. Which will validate the ProcedureDefinitionStmt itself after validating that
      // the required signature is followed.
      for (ContractProcedureImplementationStmt implementationStmt : this.contractProcedureImplementationStmts) {
        implementationStmt.assertExpectedExprTypes(
            scopedHeap, canonicalImplementationName, this.contractDefinitionStmt, this.concreteImplementationTypeParams);
      }

      // Finally, add this implementation to the scoped heap so that it can't be re-implemented.
      Optional<String> optionalDefiningModuleDisambiguator =
          Optional.ofNullable(ScopedHeap.currProgramDepModules.rowMap().get("$THIS_MODULE$"))
              .map(m -> m.values().stream().findFirst().get().getUniqueModuleName());
      scopedHeap.putIdentifierValue(
          this.canonicalImplementationName,
          Types.$ContractImplementation.forContractNameAndConcreteTypeParams(
              this.contractName,
              optionalDefiningModuleDisambiguator,
              this.concreteImplementationTypeParams.values().asList()
          ),
          this.implementationName
      );
      scopedHeap.markIdentifierUsed(this.canonicalImplementationName);
      scopedHeap.putIdentifierValue(
          this.implementationName,
          Types.$ContractImplementation.forContractNameAndConcreteTypeParams(
              this.contractName,
              optionalDefiningModuleDisambiguator,
              this.concreteImplementationTypeParams.values().asList()
          ),
          null
      );
      scopedHeap.markIdentifierUsed(this.implementationName);
    }
  }

  public static String getContractTypeString(String contractName, List<String> typeParams) {
    return String.format(
        "%s<%s>",
        contractName,
        String.join(", ", typeParams)
    );
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder res =
        new StringBuilder("  /* ")
            .append(this.canonicalImplementationName)
            .append(" */\n  public static final class ")
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
