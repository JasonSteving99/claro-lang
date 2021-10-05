package com.claro.examples.calculator_example.intermediate_representation.statements;

import com.claro.examples.calculator_example.intermediate_representation.expressions.Expr;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;


public class ProviderFunctionDefinitionStmt extends ProcedureDefinitionStmt {

  public ProviderFunctionDefinitionStmt(
      String providerName,
      Type outputType,
      StmtListNode stmtListNode,
      Expr returnExpr) {
    super(
        providerName,
        Types.ProcedureType.ProviderType.forReturnType(outputType),
        ImmutableList.of(stmtListNode, returnExpr)
    );
  }

  public ProviderFunctionDefinitionStmt(
      String providerName,
      Type outputType,
      Expr returnExpr) {
    super(
        providerName,
        Types.ProcedureType.ProviderType.forReturnType(outputType),
        ImmutableList.of(returnExpr)
    );
  }
}
