package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.claro.runtime_utilities.injector.InjectedKey;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.util.Optional;
import java.util.stream.Collectors;


public class ConsumerFunctionDefinitionStmt extends ProcedureDefinitionStmt {
  public ConsumerFunctionDefinitionStmt(
      String consumerName,
      ImmutableMap<String, TypeProvider> argTypes,
      StmtListNode stmtListNode) {
    this(consumerName, argTypes, Optional.empty(), stmtListNode);
  }

  public ConsumerFunctionDefinitionStmt(
      String consumerName,
      ImmutableMap<String, TypeProvider> argTypes,
      Optional<ImmutableList<InjectedKey>> optionalInjectedKeysTypes,
      StmtListNode stmtListNode) {
    super(
        consumerName,
        argTypes,
        optionalInjectedKeysTypes,
        (thisProcedureDefinitionStmt) ->
            (scopedHeap) ->
                Types.ProcedureType.ConsumerType.forConsumerArgTypes(
                    argTypes.values()
                        .stream()
                        .map(t -> t.resolveType(scopedHeap))
                        .collect(ImmutableList.toImmutableList()),
                    optionalInjectedKeysTypes
                        .map(
                            injectedKeysTypes ->
                                injectedKeysTypes.stream()
                                    .map(
                                        injectedKey ->
                                            new Key(injectedKey.name, injectedKey.typeProvider.resolveType(scopedHeap)))
                                    .collect(Collectors.toSet())
                        )
                        .orElse(Sets.newHashSet()),
                    thisProcedureDefinitionStmt,
                    () ->
                        ProcedureDefinitionStmt.optionalActiveProcedureDefinitionStmt
                            .map(activeProcedureDefinitionStmt -> activeProcedureDefinitionStmt.resolvedProcedureType)
                ),
        stmtListNode
    );
  }

  // Use this constructor for a Consumer function declared using a Lambda form so that we can use the
  // custom BaseType that will perform the correct codegen.
  public ConsumerFunctionDefinitionStmt(
      String consumerName,
      BaseType baseType,
      ImmutableMap<String, TypeProvider> argTypes,
      StmtListNode stmtListNode) {
    super(
        consumerName,
        argTypes,
        Optional.empty(),
        (thisProcedureDefinitionStmt) ->
            (scopedHeap) ->
                Types.ProcedureType.ConsumerType.forConsumerArgTypes(
                    argTypes.values()
                        .stream()
                        .map(t -> t.resolveType(scopedHeap))
                        .collect(ImmutableList.toImmutableList()),
                    baseType,
                    Sets.newHashSet(),
                    thisProcedureDefinitionStmt,
                    () ->
                        ProcedureDefinitionStmt.optionalActiveProcedureDefinitionStmt
                            .map(activeProcedureDefinitionStmt -> activeProcedureDefinitionStmt.resolvedProcedureType)
                ),
        stmtListNode
    );
  }
}
