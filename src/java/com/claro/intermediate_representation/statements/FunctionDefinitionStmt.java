package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;


public class FunctionDefinitionStmt extends ProcedureDefinitionStmt {

  public FunctionDefinitionStmt(
      String functionName,
      ImmutableMap<String, TypeProvider> argTypes,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode,
      Expr returnExpr) {
    super(
        functionName,
        argTypes,
        (scopedHeap) ->
            Types.ProcedureType.FunctionType.forArgsAndReturnTypes(
                argTypes.values().stream().map(t -> t.resolveType(scopedHeap)).collect(ImmutableList.toImmutableList()),
                outputTypeProvider.resolveType(scopedHeap)
            ),
        ImmutableList.of(stmtListNode, returnExpr)
    );
  }

  public FunctionDefinitionStmt(
      String functionName,
      ImmutableMap<String, TypeProvider> argTypes,
      TypeProvider outputTypeProvider,
      Expr returnExpr) {
    super(
        functionName,
        argTypes,
        (scopedHeap) ->
            Types.ProcedureType.FunctionType.forArgsAndReturnTypes(
                argTypes.values().stream().map(t -> t.resolveType(scopedHeap)).collect(ImmutableList.toImmutableList()),
                outputTypeProvider.resolveType(scopedHeap)
            ),
        ImmutableList.of(returnExpr)
    );
  }
}
