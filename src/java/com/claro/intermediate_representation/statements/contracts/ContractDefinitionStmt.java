package com.claro.intermediate_representation.statements.contracts;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class ContractDefinitionStmt extends Stmt {

  private final String contractName;

  private boolean alreadyAssertedTypes = false;
  public final ImmutableList<String> typeParamNames;
  public final ImmutableMap<String, ContractProcedureSignatureDefinitionStmt> declaredContractSignaturesByProcedureName;

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
      scopedHeap.enterNewScope();
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
      InternalStaticStateUtil.ContractDefinitionStmt_currentContractName = null;
      InternalStaticStateUtil.ContractDefinitionStmt_currentContractGenericTypeParamNames = null;
      scopedHeap.exitCurrScope();

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

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // TODO(steving): IMPLEMENT DYNAMIC DISPATCH OVER CONTRACTS
    return GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder("/* TODO(steving): IMPLEMENT DYNAMIC DISPATCH OVER CONTRACTS */"));
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return null;
  }
}
