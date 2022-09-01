package com.claro.internal_static_state;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

// TODO(steving) Eventually all static centralized state should be moved here to avoid fighting circular deps ever again.
// It's just become too unwieldy to actually have each class manage its own centralized static state given that the
// cross layer build deps become a huge pain with circular deps. So this file serves as a util with intentionally
// minimal dependencies, specifically none that go into the intermediate_representation/(expressions|statements)
// packages to avoid all circular deps.
public class InternalStaticStateUtil {
  public static ImmutableMap<String, TypeProvider> GraphProcedureDefinitionStmt_graphFunctionArgs;
  public static Optional<ImmutableMap<String, TypeProvider>>
      GraphProcedureDefinitionStmt_graphFunctionOptionalInjectedKeys;
  public static HashSet<String> GraphProcedureDefinitionStmt_usedGraphNodesNamesSet = new HashSet<>();

  // This is to be used during the parsing phase so that whenever a GraphNodeReference is legally identified the
  // referenced node will be added to this list so that this GraphNodeDefinitionStmt knows which upstream deps it needs
  // to gen code for. Since GraphNodeReferences are only valid within the scope of matching a GraphNodeDefinition this
  // is valid and safe.
  public static ImmutableList.Builder<String> GraphNodeDefinitionStmt_upstreamGraphNodeReferencesBuilder =
      ImmutableList.builder();
  // This set of upstream deps refers to the same node references as the above list, but membership in this set
  // indicates that the reference is implicitly indicating the user wants control over lazy evaluation of the node
  // via access to the subgraph as a provider to execute on demand instead of depending on the already computed result.
  public static ImmutableSet.Builder<String> GraphNodeDefinitionStmt_upstreamGraphNodeProviderReferencesBuilder =
      ImmutableSet.builder();

  // I need a mechanism for easily communicating to sub-nodes in the AST that they are a part of a
  // ProcedureDefinitionStmt so that during type validation, nested procedure call nodes know to update
  // the active instance's used injected keys set.
  public static Optional<Object> ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt = Optional.empty();
  public static Optional<Type> ProcedureDefinitionStmt_optionalActiveProcedureResolvedType = Optional.empty();

  // This field helps establish that we are in fact within a Pipe Chain context, which will allow the pipe chain
  // backreference sigil to be available.
  public static boolean PipeChainStmt_withinPipeChainContext;
  public static AtomicReference<Type> PipeChainStmt_backreferencedPipeChainStageType;
  public static int PipeChainStmt_backreferenceUsagesCount = 0;
  public static AtomicReference<BiFunction<ScopedHeap, Boolean, Object>>
      PipeChainStmt_backreferencedPipeChainStageCodegenFn =
      new AtomicReference<>();
}
