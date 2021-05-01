package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;


public class FunctionDefinitionStmt extends ProcedureDefinitionStmt {

  public FunctionDefinitionStmt(
      String functionName,
      ImmutableMap<String, Type> argTypes,
      Type outputType,
      StmtListNode stmtListNode,
      Expr returnExpr) {
    super(
        functionName,
        argTypes,
        Types.ProcedureType.FunctionType.forArgsAndReturnTypes(argTypes.values().asList(), outputType),
        ImmutableList.of(stmtListNode, returnExpr)
    );
  }

  public FunctionDefinitionStmt(
      String functionName,
      ImmutableMap<String, Type> argTypes,
      Type outputType,
      Expr returnExpr) {
    super(
        functionName,
        argTypes,
        Types.ProcedureType.FunctionType.forArgsAndReturnTypes(argTypes.values().asList(), outputType),
        ImmutableList.of(returnExpr)
    );
  }
}
