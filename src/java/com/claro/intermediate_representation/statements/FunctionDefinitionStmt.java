package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.claro.runtime_utilities.injector.InjectedKey;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Optional;


public class FunctionDefinitionStmt extends ProcedureDefinitionStmt {

  public FunctionDefinitionStmt(
      String functionName,
      ImmutableMap<String, TypeProvider> argTypes,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode) {
    this(functionName, argTypes, Optional.empty(), outputTypeProvider, stmtListNode);
  }

  public FunctionDefinitionStmt(
      String functionName,
      ImmutableMap<String, TypeProvider> argTypes,
      Optional<ImmutableList<InjectedKey>> optionalInjectedKeysTypes,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode) {
    super(
        functionName,
        argTypes,
        optionalInjectedKeysTypes,
        (scopedHeap) ->
            Types.ProcedureType.FunctionType.forArgsAndReturnTypes(
                argTypes.values().stream().map(t -> t.resolveType(scopedHeap)).collect(ImmutableList.toImmutableList()),
                outputTypeProvider.resolveType(scopedHeap)
            ),
        stmtListNode
    );
  }

  // Use this constructor for a Function declared using a Lambda form so that we can use the
  // custom BaseType that will perform the correct codegen.
  public FunctionDefinitionStmt(
      String functionName,
      BaseType baseType,
      ImmutableMap<String, TypeProvider> argTypes,
      TypeProvider outputTypeProvider,
      StmtListNode stmtListNode) {
    super(
        functionName,
        argTypes,
        Optional.empty(),
        (scopedHeap) ->
            Types.ProcedureType.FunctionType.forArgsAndReturnTypes(
                argTypes.values().stream().map(t -> t.resolveType(scopedHeap)).collect(ImmutableList.toImmutableList()),
                outputTypeProvider.resolveType(scopedHeap),
                baseType
            ),
        stmtListNode
    );
  }
}
