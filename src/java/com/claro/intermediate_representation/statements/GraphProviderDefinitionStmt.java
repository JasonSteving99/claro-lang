package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.claro.runtime_utilities.injector.InjectedKeyIdentifier;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.util.Optional;
import java.util.stream.Collectors;

public class GraphProviderDefinitionStmt extends GraphProcedureDefinitionStmt {

  public GraphProviderDefinitionStmt(
      String graphFunctionName,
      TypeProvider outputTypeProvider,
      GraphNodeDefinitionStmt rootNode,
      ImmutableList<GraphNodeDefinitionStmt> nonRootNodes) {
    this(graphFunctionName, Optional.empty(), outputTypeProvider, rootNode, nonRootNodes);
  }

  public GraphProviderDefinitionStmt(
      String graphFunctionName,
      Optional<ImmutableList<InjectedKeyIdentifier>> optionalInjectedKeysTypes,
      TypeProvider outputTypeProvider,
      GraphNodeDefinitionStmt rootNode,
      ImmutableList<GraphNodeDefinitionStmt> nonRootNodes) {
    super(
        graphFunctionName,
        ImmutableMap.of(),
        optionalInjectedKeysTypes,
        (thisProcedureDefinitionStmt) ->
            (scopedHeap) ->
                Types.ProcedureType.ProviderType.forReturnType(
                    outputTypeProvider.resolveType(scopedHeap),
                    optionalInjectedKeysTypes
                        .map(
                            injectedKeysTypes ->
                                injectedKeysTypes.stream()
                                    .map(
                                        injectedKey ->
                                            Key.create(injectedKey.getName()
                                                           .getIdentifier(), injectedKey.getTypeProvider()
                                                           .resolveType(scopedHeap)))
                                    .collect(Collectors.toSet())
                        )
                        .orElse(Sets.newHashSet()),
                    thisProcedureDefinitionStmt,
                    /*explicitlyAnnotatedBlocking=*/ false
                ),
        Optional.of(outputTypeProvider),
        rootNode,
        nonRootNodes
    );
  }

}
