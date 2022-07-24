package com.claro.internal_static_state;

import com.claro.intermediate_representation.types.TypeProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.HashSet;
import java.util.Optional;

// It's just become too unwieldy to actually have each class manage its own centralized static state given that the
// cross layer build deps become a huge pain with circular deps. So this file serves as a util with intentionally
// minimal dependencies, specifically none that go into the intermediate_representation/(expressions|statements)
// packages to avoid all circular deps.
// TODO(steving) Eventually all static centralized state should be moved here to avoid fighting circular deps ever again.
public class InternalStaticStateUtil {
  public static boolean GraphFunctionDefinitionStmt_withinGraphFunctionDefinition = false;
  public static ImmutableMap<String, TypeProvider> GraphFunctionDefinitionStmt_graphFunctionArgs;
  public static Optional<ImmutableMap<String, TypeProvider>>
      GraphFunctionDefinitionStmt_graphFunctionOptionalInjectedKeys;

  // This is to be used during the parsing phase so that whenever a GraphNodeReference is legally identified the
  // referenced node will be added to this list so that this GraphNodeDefinitionStmt knows which upstream deps it needs
  // to gen code for. Since GraphNodeReferences are only valid within the scope of matching a GraphNodeDefinition this
  // is valid and safe.
  public static ImmutableList.Builder<String> GraphNodeDefinitionStmt_upstreamGraphNodeReferencesBuilder =
      ImmutableList.builder();
  public static HashSet<String> GraphFunctionDefinitionStmt_usedGraphNodesNamesSet = new HashSet<>();
}
