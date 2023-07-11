package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.claro.runtime_utilities.injector.InjectedKey;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Optional;
import java.util.stream.Collectors;


public class ConsumerFunctionDefinitionStmt extends ProcedureDefinitionStmt {
  public ConsumerFunctionDefinitionStmt(
      String consumerName,
      ImmutableMap<String, TypeProvider> argTypes,
      StmtListNode stmtListNode,
      Boolean explicitlyAnnotatedBlocking) {
    this(consumerName, argTypes, Optional.empty(), stmtListNode, explicitlyAnnotatedBlocking, Optional.empty());
  }

  public ConsumerFunctionDefinitionStmt(
      String consumerName,
      ImmutableMap<String, TypeProvider> argTypes,
      Optional<ImmutableList<InjectedKey>> optionalInjectedKeysTypes,
      StmtListNode stmtListNode,
      Boolean explicitlyAnnotatedBlocking,
      Optional<ImmutableList<String>> optionalGenericBlockingOnArgs) {
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
                    BaseType.CONSUMER_FUNCTION,
                    optionalInjectedKeysTypes
                        .map(
                            injectedKeysTypes ->
                                injectedKeysTypes.stream()
                                    .map(
                                        injectedKey ->
                                            new Key(injectedKey.getName(), injectedKey.getTypeProvider()
                                                .resolveType(scopedHeap)))
                                    .collect(Collectors.toSet())
                        )
                        .orElse(Sets.newHashSet()),
                    thisProcedureDefinitionStmt,
                    explicitlyAnnotatedBlocking,
                    optionalGenericBlockingOnArgs
                        .map(genericBlockingOnArgs ->
                                 mapArgNamesToIndex(genericBlockingOnArgs, argTypes.keySet().asList()))
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
                    /*explicitlyAnnotatedBlocking=*/false,
                    Optional.empty()
                ),
        stmtListNode
    );
  }

  private static ImmutableSet<Integer> mapArgNamesToIndex(ImmutableList<String> argNames, ImmutableList<String> args) {
    ImmutableSet.Builder<Integer> res = ImmutableSet.builder();
    for (int i = 0; i < args.size(); i++) {
      if (argNames.contains(args.get(i))) {
        res.add(i);
      }
    }
    return res.build();
  }
}
