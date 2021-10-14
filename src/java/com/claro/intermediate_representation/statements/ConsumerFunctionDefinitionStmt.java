package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;


public class ConsumerFunctionDefinitionStmt extends ProcedureDefinitionStmt {
  public ConsumerFunctionDefinitionStmt(
      String consumerName,
      ImmutableMap<String, TypeProvider> argTypes,
      StmtListNode stmtListNode) {
    super(
        consumerName,
        argTypes,
        (scopedHeap) ->
            Types.ProcedureType.ConsumerType.forConsumerArgTypes(
                argTypes.values()
                    .stream()
                    .map(t -> t.resolveType(scopedHeap))
                    .collect(ImmutableList.toImmutableList())),
        ImmutableList.of(stmtListNode)
    );
  }
}
