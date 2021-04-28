package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;


public class ConsumerFunctionDefinitionStmt extends ProcedureDefinitionStmt {
  public ConsumerFunctionDefinitionStmt(
      String consumerName,
      ImmutableMap<String, Type> argTypes,
      StmtListNode stmtListNode) {
    super(
        ImmutableList.of(stmtListNode),
        Types.ProcedureType.ConsumerType.forConsumerArgTypes(consumerName, argTypes)
    );
  }
}
