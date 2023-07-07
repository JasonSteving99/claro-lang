package com.claro.intermediate_representation;

import com.claro.intermediate_representation.statements.contracts.ContractProcedureSignatureDefinitionStmt;
import com.google.common.collect.ImmutableList;

public class ModuleNode {
  public final ImmutableList<ContractProcedureSignatureDefinitionStmt> exportedSignatures;
  private final String moduleName;
  private final String javaPackage;

  public ModuleNode(
      ImmutableList<ContractProcedureSignatureDefinitionStmt> exportedSignatures,
      String moduleName,
      String javaPackage) {
    this.exportedSignatures = exportedSignatures;
    this.moduleName = moduleName;
    this.javaPackage = javaPackage;
  }

  // TODO(steving) Implement some method that can be used to validate whether a given ScopedHeap contains all of the
  //   expected signatures parsed in the module definition.
}
