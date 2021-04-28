package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;


public class ProviderFunctionDefinitionStmt extends ProcedureDefinitionStmt {

  public ProviderFunctionDefinitionStmt(
      String functionName,
      Type outputType,
      StmtListNode stmtListNode,
      Expr returnExpr) {
    super(
        ImmutableList.of(stmtListNode, returnExpr),
        Types.ProcedureType.ProviderType.forReturnType(functionName, outputType)
    );
  }

  public ProviderFunctionDefinitionStmt(
      String functionName,
      Type outputType,
      Expr returnExpr) {
    super(
        ImmutableList.of(returnExpr),
        Types.ProcedureType.ProviderType.forReturnType(functionName, outputType)
    );
  }
}
