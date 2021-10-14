package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;


public class ProviderFunctionDefinitionStmt extends ProcedureDefinitionStmt {

  public ProviderFunctionDefinitionStmt(
      String providerName,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode,
      Expr returnExpr) {
    super(
        providerName,
        (scopedHeap) -> Types.ProcedureType.ProviderType.forReturnType(outputTypeProvider.resolveType(scopedHeap)),
        ImmutableList.of(stmtListNode, returnExpr)
    );
  }

  public ProviderFunctionDefinitionStmt(
      String providerName,
      TypeProvider outputTypeProvider,
      Expr returnExpr) {
    super(
        providerName,
        (scopedHeap) -> Types.ProcedureType.ProviderType.forReturnType(outputTypeProvider.resolveType(scopedHeap)),
        ImmutableList.of(returnExpr)
    );
  }
}
