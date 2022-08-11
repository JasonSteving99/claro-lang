package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.base.Preconditions;

import java.util.function.Supplier;

public class GraphNodeReferenceExpr extends IdentifierReferenceTerm {
  private final String referencedGraphNodeName;
  private boolean lazyProviderInjectionRequested = false;

  public GraphNodeReferenceExpr(String referencedGraphNodeName, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(referencedGraphNodeName, currentLine, currentLineNumber, startCol, endCol);
    this.referencedGraphNodeName = referencedGraphNodeName;
    InternalStaticStateUtil.GraphNodeDefinitionStmt_upstreamGraphNodeReferencesBuilder.add(this.referencedGraphNodeName);
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    // For the sake of injecting a subgraph lazily as a provider<Foo> instead of Foo itself we'll intercept type
    // assertions that explicitly constrain the type to be provider<> and that way generate the appropriate lazy code.
    if (expectedExprType.baseType().equals(BaseType.PROVIDER_FUNCTION)) {
      this.lazyProviderInjectionRequested = true;
    }
    super.assertExpectedExprType(scopedHeap, expectedExprType);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) {
    // Make sure we check this will actually be a valid reference before we allow it. We validate that we're actually
    // looking up a node definition by making sure that we use the internal renaming format for graph nodes (starting
    // with '@' means it can't be user-defined).
    String internalGraphNodeName = String.format("@%s", this.referencedGraphNodeName);
    Preconditions.checkState(
        InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.isPresent() &&
        ((Types.ProcedureType) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureResolvedType.get())
            .getIsGraph()
            .get(),
        "Unexpected node reference <%s> outside of graph scope!",
        internalGraphNodeName
    );
    Preconditions.checkState(
        scopedHeap.isIdentifierDeclared(internalGraphNodeName),
        "No node <%s> within the current graph!",
        internalGraphNodeName
    );

    scopedHeap.markIdentifierUsed(internalGraphNodeName);

    // Because graph nodes can be defined in any order within the graph, we need to be able to recursively resolve types
    // of referenced nodes in depth-first order.
    // TODO(steving) Claro already knows how to convert this type from future<Foo> to just raw unwrapped Foo while still
    //  being type safe. But, potential extensions exist for other sorts of things like mapping to just a single element
    //  of a [Foo] from some upstream dep.
    Type nodeInjectedType = TypeProvider.Util.getTypeByName(
        internalGraphNodeName, /*isTypeDefinition=*/ false).resolveType(scopedHeap);

    // Claro supports some useful auto-magic injection options such as allowing the user to lazily depend on the
    // referenced sub-graph, and automatically unwrapping a future. Determine if we're doing any of these.

    boolean referencedNodeIsFuture = false;
    if (nodeInjectedType.baseType().equals(BaseType.FUTURE)) {
      referencedNodeIsFuture = true;
    }

    // If lazy provider injection is being requested, go ahead and wrap the nodeInjectedType as a provider<nodeInjectedType>.
    if (this.lazyProviderInjectionRequested) {
      InternalStaticStateUtil.GraphNodeDefinitionStmt_upstreamGraphNodeProviderReferencesBuilder
          .add(this.referencedGraphNodeName);
      if (!referencedNodeIsFuture) {
        // Lazy providers necessarily return a future<Foo> since the subgraph should still run asynchronously if the
        // user decides to call it, and there's no blocking within a graph procedure.
        nodeInjectedType = Types.FutureType.wrapping(nodeInjectedType);
      }
      nodeInjectedType = Types.ProcedureType.ProviderType.typeLiteralForReturnType(
          nodeInjectedType, /*explicitlyAnnotatedBlocking=*/false);
    } else if (referencedNodeIsFuture) {
      // If the referenced Graph Node is in fact a wrapped future, then we'll codegen unwrapping code. Declare the type as
      // the unwrapped type.
      nodeInjectedType = nodeInjectedType.parameterizedTypeArgs().get("$value");
    }

    // Mark the node used.
    InternalStaticStateUtil.GraphFunctionDefinitionStmt_usedGraphNodesNamesSet.add(this.referencedGraphNodeName);

    return nodeInjectedType;
  }
}
