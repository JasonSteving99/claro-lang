package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;


public class ProviderFunctionDefinitionStmt extends ProcedureDefinitionStmt {

  public ProviderFunctionDefinitionStmt(
      String providerName,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode) {
    super(
        providerName,
        (scopedHeap) -> Types.ProcedureType.ProviderType.forReturnType(outputTypeProvider.resolveType(scopedHeap)),
        stmtListNode
    );
  }
}
