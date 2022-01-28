package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.types.BaseType;
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

  // Use this constructor for a Provider function declared using a Lambda form so that we can use the
  // custom BaseType that will perform the correct codegen.
  public ProviderFunctionDefinitionStmt(
      String providerName,
      BaseType baseType,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode) {
    super(
        providerName,
        (scopedHeap) ->
            Types.ProcedureType.ProviderType.forReturnType(outputTypeProvider.resolveType(scopedHeap), baseType),
        stmtListNode
    );
  }
}
