package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.base.Preconditions;

import java.util.function.Supplier;

public class GraphNodeReferenceExpr extends IdentifierReferenceTerm {
  private String referencedGraphNodeName;

  public GraphNodeReferenceExpr(String referencedGraphNodeName, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(referencedGraphNodeName, currentLine, currentLineNumber, startCol, endCol);
    this.referencedGraphNodeName = referencedGraphNodeName;
    InternalStaticStateUtil.GraphNodeDefinitionStmt_upstreamGraphNodeReferencesBuilder.add(this.referencedGraphNodeName);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) {
    // Make sure we check this will actually be a valid reference before we allow it. We validate that we're actually
    // looking up a node definition by making sure that we use the internal renaming format for graph nodes (starting
    // with '@' means it can't be user-defined).
    String internalGraphNodeName = String.format("@%s", this.referencedGraphNodeName);
    Preconditions.checkState(
        InternalStaticStateUtil.GraphFunctionDefinitionStmt_withinGraphFunctionDefinition,
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
    Type nodeType = TypeProvider.Util.getTypeByName(
        internalGraphNodeName, /*isTypeDefinition=*/ false).resolveType(scopedHeap);

    // If this Graph Node is in fact a wrapped future, then we'll codegen unwrapping code. Declare the type as the
    // unwrapped type.
    if (nodeType.baseType().equals(BaseType.FUTURE)) {
      nodeType = ((Types.FutureType) nodeType).parameterizedTypeArgs().get("$value");
    }

    // Mark the node used.
    InternalStaticStateUtil.GraphFunctionDefinitionStmt_usedGraphNodesNamesSet.add(this.referencedGraphNodeName);

    return nodeType;
  }
}
