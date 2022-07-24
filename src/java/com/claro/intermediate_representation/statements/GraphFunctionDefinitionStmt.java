package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.runtime_utilities.injector.InjectedKey;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GraphFunctionDefinitionStmt extends ProcedureDefinitionStmt {

  private final GraphNodeDefinitionStmt rootNode;
  private final ImmutableList<GraphNodeDefinitionStmt> nonRootNodes;
  private final ImmutableMap<String, TypeProvider> graphFunctionArgs;
  private final Optional<ImmutableMap<String, TypeProvider>> graphFunctionOptionalInjectedKeys;
  private boolean alreadyAssertedTypes = false;

  public GraphFunctionDefinitionStmt(
      String graphFunctionName,
      ImmutableMap<String, TypeProvider> argTypes,
      TypeProvider outputTypeProvider,
      GraphNodeDefinitionStmt rootNode,
      ImmutableList<GraphNodeDefinitionStmt> nonRootNodes) {
    this(graphFunctionName, argTypes, Optional.empty(), outputTypeProvider, rootNode, nonRootNodes);
  }

  public GraphFunctionDefinitionStmt(
      String graphFunctionName,
      ImmutableMap<String, TypeProvider> argTypes,
      Optional<ImmutableList<InjectedKey>> optionalInjectedKeysTypes,
      TypeProvider outputTypeProvider,
      GraphNodeDefinitionStmt rootNode,
      ImmutableList<GraphNodeDefinitionStmt> nonRootNodes) {
    super(
        graphFunctionName,
        argTypes,
        optionalInjectedKeysTypes,
        (thisProcedureDefinitionStmt) ->
            (scopedHeap) ->
                Types.ProcedureType.FunctionType.forArgsAndReturnTypes(
                    argTypes.values()
                        .stream()
                        .map(t -> t.resolveType(scopedHeap))
                        .collect(ImmutableList.toImmutableList()),
                    outputTypeProvider.resolveType(scopedHeap),
                    BaseType.FUNCTION, // Remember that Claro's Graph Functions are "just" sugar over plain functions.
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
        // We'll allow the superclass to own all of the type checking logic since that is quite complex for procedure
        // definition stmts and I *really* don't want to duplicate that in more than one place.
        GraphFunctionDefinitionStmt.getFunctionBodyStmt(rootNode, nonRootNodes, outputTypeProvider, graphFunctionName)
    );

    this.rootNode = rootNode;
    this.nonRootNodes = nonRootNodes;

    // Make sure that we make the function args available to the GraphNodeDefinitionStmt's code gen.
    graphFunctionArgs =
        argTypes.entrySet().stream()
            .collect(
                ImmutableMap.toImmutableMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                ));
    // Make sure that we make the function's used bindings available to the GraphNodeDefinitionStmt's code gen.
    graphFunctionOptionalInjectedKeys =
        optionalInjectedKeysTypes.map(
            injectedKeysTypes -> injectedKeysTypes.stream()
                .collect(
                    ImmutableMap.toImmutableMap(
                        injectedKey -> injectedKey.optionalAlias.orElse(injectedKey.name),
                        injectedKey -> injectedKey.typeProvider
                    )));
  }

  private static StmtListNode getFunctionBodyStmt(
      GraphNodeDefinitionStmt rootNode,
      ImmutableList<GraphNodeDefinitionStmt> nonRootNodes,
      TypeProvider outputTypeProvider,
      String graphFunctionName) {
    return new StmtListNode(
        new ReturnStmt(
            new Expr(ImmutableList.of(), () -> "", -1, -1, -1) {
              @Override
              public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
                return outputTypeProvider.resolveType(scopedHeap);
              }

              @Override
              public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
                return new StringBuilder("new $")
                    .append(graphFunctionName)
                    .append("_graphAsyncImpl().$")
                    .append(rootNode.nodeName)
                    .append("_nodeAsync(")
                    .append(
                        String.join(
                            ", ", InternalStaticStateUtil.GraphFunctionDefinitionStmt_graphFunctionArgs.keySet()))
                    .append(
                        InternalStaticStateUtil.GraphFunctionDefinitionStmt_graphFunctionOptionalInjectedKeys
                            .map(injectedKeys -> injectedKeys.keySet()
                                .stream()
                                .collect(Collectors.joining(", ", ", ", "")))
                            .orElse("")
                    )
                    .append(")");
              }

              @Override
              public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
                // TODO(steving) Need to think through the interpreted implementation of all of this.
                return null;
              }
            },
            new AtomicReference<>(outputTypeProvider)
        )
    );
  }

  @Override
  public void registerProcedureTypeProvider(ScopedHeap scopedHeap) {
    // We definitely want to register the wrapper function.
    super.registerProcedureTypeProvider(scopedHeap);

    // Need to assert the expected return type on the Root node only since it will recursively validate the remaining
    // nodes.
    rootNode.optionalExpectedNodeType = Optional.of(this.resolvedProcedureType.getReturnType());
  }

  @Override
  protected void subclassSetupFunctionBodyScopeCallback(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Now I need to register all of the node definitions in the ScopedHeap before I can do type validation since
    // this type registration is utilized for the out-of-order recursive node traversal.
    this.rootNode.registerGraphNodeTypeProvider(scopedHeap);
    this.nonRootNodes.forEach(node -> node.registerGraphNodeTypeProvider(scopedHeap));
    // Now since there's no direct reference to the root node in the wrapper function, we need to make sure that we
    // mark it as used manually.
    // Replace the TypeProvider found in the symbol table with the actual resolved type.
    String internalGraphNodeName = String.format("@%s", this.rootNode.nodeName);
    scopedHeap.putIdentifierValue(
        internalGraphNodeName,
        this.resolvedProcedureType.getReturnType(),
        null
    );
    scopedHeap.markIdentifierUsed(internalGraphNodeName);
    InternalStaticStateUtil.GraphFunctionDefinitionStmt_usedGraphNodesNamesSet.add(this.rootNode.nodeName);

    // I need to actually assert types on the GraphNodeDefinitionStmts. Asserting the root node will
    // transitively cover the remaining nodes since we'll validate that we have a connected graph.
    rootNode.assertExpectedExprTypes(scopedHeap);

    // Graph Node names should all be unique.
    HashSet<String> uniqueNodeNamesSet = new HashSet<>(nonRootNodes.size() + 1);
    HashSet<String> duplicatedNodeNamesSet = new HashSet<>();
    uniqueNodeNamesSet.add(this.rootNode.nodeName);
    this.nonRootNodes.forEach(
        node -> {
          if (!uniqueNodeNamesSet.add(node.nodeName)) {
            duplicatedNodeNamesSet.add(node.nodeName);
          }
        });
    if (duplicatedNodeNamesSet.size() > 0) {
      // In this case, there's a redeclaration of some node
      throw ClaroTypeException.forGraphFunctionWithDuplicatedNodeNames(this.procedureName, duplicatedNodeNamesSet);
    }

    // Validate that all of the nodes were actually connected.
    Set<String> unusedNodes = Sets.difference(
        uniqueNodeNamesSet, InternalStaticStateUtil.GraphFunctionDefinitionStmt_usedGraphNodesNamesSet);
    if (!unusedNodes.isEmpty()) {
      throw ClaroTypeException.forGraphFunctionWithUnconnectedNodes(this.procedureName, unusedNodes);
    }
    InternalStaticStateUtil.GraphFunctionDefinitionStmt_usedGraphNodesNamesSet.clear();
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // The only customization that I need of the inherited type checking is to assert that we do not duplicate type
    // checking (since the ProcedureDefinitionStmt has some complex logic where it will traverse function calls to
    // assert types on called functions (which would include this one again)). Then we just need to assert that we
    // are in a GraphFunctionDefinition scope so that GraphNodeDefinitionStmts are marked as allowed.
    if (!this.alreadyAssertedTypes) {
      this.alreadyAssertedTypes = true;

      // Graph functions are required to return a future<Foo> because this way Claro can make thread safety assertions.
      if (!this.resolvedProcedureType.getReturnType().baseType().equals(BaseType.FUTURE)) {
        throw ClaroTypeException.forGraphFunctionNotReturningFuture(this.procedureName, this.resolvedProcedureType);
      }

      InternalStaticStateUtil.GraphFunctionDefinitionStmt_withinGraphFunctionDefinition = true;

      super.assertExpectedExprTypes(scopedHeap);

      InternalStaticStateUtil.GraphFunctionDefinitionStmt_withinGraphFunctionDefinition = false;
    }
  }

  @Override
  protected Optional<GeneratedJavaSource> getHelperMethodsJavaSource(ScopedHeap scopedHeap) {
    // First time seeing these nodes during codegen phase, so make sure that they're placed in the scopedheap.
    Consumer<GraphNodeDefinitionStmt> putNodeDefInScopedHeap = (node) -> {
      scopedHeap.putIdentifierValue(node.nodeName, node.actualNodeType);
      scopedHeap.initializeIdentifier(node.nodeName);
    };
    putNodeDefInScopedHeap.accept(rootNode);
    nonRootNodes.forEach(putNodeDefInScopedHeap);

    // Since the call to the result node is done via generated code, it needs to be marked "used" manually.
    scopedHeap.markIdentifierUsed(rootNode.nodeName);

    // Need to wrap the functions generated for the nodes in an internal helper class so that the graph function may be
    // called multiple times concurrently without the cache being accidentally reused.
    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder()
            .append("\tprivate static class $")
            .append(this.procedureName)
            .append("_graphAsyncImpl {\n"));

    res = res.createMerged(rootNode.generateJavaSourceOutput(scopedHeap));
    for (GraphNodeDefinitionStmt node : nonRootNodes) {
      res = res.createMerged(node.generateJavaSourceOutput(scopedHeap));
    }

    // Finish wrapping in internal helper class.
    res.javaSourceBody().append("\t}\n");

    return Optional.of(res);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Quickly need to setup the static state so that the GraphNodeDefinitionStmts have what they need.
    InternalStaticStateUtil.GraphFunctionDefinitionStmt_graphFunctionArgs = graphFunctionArgs;
    InternalStaticStateUtil.GraphFunctionDefinitionStmt_graphFunctionOptionalInjectedKeys =
        graphFunctionOptionalInjectedKeys;

    GeneratedJavaSource res = super.generateJavaSourceOutput(scopedHeap);

    // Teardown the static state so the next GraphFunctionDefinitionStmt has a clean slate.
    InternalStaticStateUtil.GraphFunctionDefinitionStmt_graphFunctionArgs = null;
    InternalStaticStateUtil.GraphFunctionDefinitionStmt_graphFunctionOptionalInjectedKeys = null;

    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Need to think through the interpreted implementation of all of this.
    return null;
  }
}
