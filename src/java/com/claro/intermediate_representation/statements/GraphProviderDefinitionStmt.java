package com.claro.intermediate_representation.statements;

import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.runtime_utilities.injector.InjectedKey;
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
      Optional<ImmutableList<InjectedKey>> optionalInjectedKeysTypes,
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
                                            new Key(injectedKey.name, injectedKey.typeProvider.resolveType(scopedHeap)))
                                    .collect(Collectors.toSet())
                        )
                        .orElse(Sets.newHashSet()),
                    thisProcedureDefinitionStmt,
                    () ->
                        InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
                            .map(activeProcedureDefinitionStmt ->
                                     ((ProcedureDefinitionStmt) activeProcedureDefinitionStmt).resolvedProcedureType),
                    /*explicitlyAnnotatedBlocking=*/ false
                ),
        outputTypeProvider,
        rootNode,
        nonRootNodes
    );
  }

}
