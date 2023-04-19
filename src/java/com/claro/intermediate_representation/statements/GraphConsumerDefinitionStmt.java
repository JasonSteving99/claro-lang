package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.claro.runtime_utilities.injector.InjectedKeyIdentifier;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.util.Optional;
import java.util.stream.Collectors;

public class GraphConsumerDefinitionStmt extends GraphProcedureDefinitionStmt {

  public GraphConsumerDefinitionStmt(
      String graphFunctionName,
      ImmutableMap<IdentifierReferenceTerm, TypeProvider> argTypes,
      GraphNodeDefinitionStmt rootNode,
      ImmutableList<GraphNodeDefinitionStmt> nonRootNodes) {
    this(graphFunctionName, argTypes, Optional.empty(), rootNode, nonRootNodes);
  }

  public GraphConsumerDefinitionStmt(
      String graphFunctionName,
      ImmutableMap<IdentifierReferenceTerm, TypeProvider> argTypes,
      Optional<ImmutableList<InjectedKeyIdentifier>> optionalInjectedKeysTypes,
      GraphNodeDefinitionStmt rootNode,
      ImmutableList<GraphNodeDefinitionStmt> nonRootNodes) {
    super(
        graphFunctionName,
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
                                            new Key(injectedKey.name.getIdentifier(), injectedKey.typeProvider.resolveType(scopedHeap)))
                                    .collect(Collectors.toSet())
                        )
                        .orElse(Sets.newHashSet()),
                    thisProcedureDefinitionStmt,
                    /*explicitlyAnnotatedBlocking=*/ false
                ),
        Optional.empty(),
        rootNode,
        nonRootNodes
    );
  }

}
