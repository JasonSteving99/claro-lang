package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;


public class ConsumerFunctionDefinitionStmt extends ProcedureDefinitionStmt {
  public ConsumerFunctionDefinitionStmt(
      String consumerName,
      ImmutableMap<String, Type> argTypes,
      StmtListNode stmtListNode) {
    super(
        consumerName,
        argTypes,
        Types.ProcedureType.ConsumerType.forConsumerArgTypes(argTypes.values().asList()),
        ImmutableList.of(stmtListNode)
    );
  }
}
