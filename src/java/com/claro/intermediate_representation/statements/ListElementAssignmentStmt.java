package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.CollectionSubscriptExpr;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.builtins_impls.collections.ClaroList;
import com.google.common.collect.ImmutableList;

public class ListElementAssignmentStmt extends Stmt {

  public ListElementAssignmentStmt(CollectionSubscriptExpr collectionSubscriptExpr, Expr e) {
    super(
        ImmutableList.of(
            /*listExpr=*/collectionSubscriptExpr.getChildren().get(0),
            /*subscriptExpr=*/collectionSubscriptExpr.getChildren().get(1),
                         e
        )
    );
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    ((IdentifierReferenceTerm) this.getChildren().get(0))
        .assertExpectedExprType(
            scopedHeap,
            Types.ListType.forValueType(
                // Get the type for the value being assigned into this List to make sure it applies.
                ((Expr) this.getChildren().get(2)).getValidatedExprType(scopedHeap))
        );
    // Can only index into Lists using Integers.
    ((Expr) this.getChildren().get(1)).assertExpectedExprType(scopedHeap, Types.INTEGER);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                "%s.set(%s, %s);\n",
                ((Expr) this.getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap),
                ((Expr) this.getChildren().get(1)).generateJavaSourceBodyOutput(scopedHeap),
                ((Expr) this.getChildren().get(2)).generateJavaSourceBodyOutput(scopedHeap)
            )
        )
    );
  }

  // Type info is lost on generateInterpretedOutput, but we know Claro only allows subscript assignment on lists.
  @SuppressWarnings("unchecked")
  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Put the computed value of this specified List element directly into ArrayList in the heap.
    ((ClaroList<Object>) this.getChildren().get(0).generateInterpretedOutput(scopedHeap))
        .set(
            (int) this.getChildren().get(1).generateInterpretedOutput(scopedHeap),
            this.getChildren().get(2).generateInterpretedOutput(scopedHeap)
        );
    return null;
  }
}
