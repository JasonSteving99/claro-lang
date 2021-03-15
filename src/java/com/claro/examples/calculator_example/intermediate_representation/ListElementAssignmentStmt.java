package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;

public class ListElementAssignmentStmt extends Stmt {

  public ListElementAssignmentStmt(ListSubscriptExpr listSubscriptExpr, Expr e) {
    super(
        ImmutableList.of(
            // TODO(steving) Assert that this is an IdentifierReferenceTerm of type List.
            /*listExpr=*/listSubscriptExpr.getChildren().get(0),
            // TODO(steving) Assert that this is an Expr of type Integer.
            /*subscriptExpr=*/listSubscriptExpr.getChildren().get(1),
            // TODO(steving) Assert that this is an Expr of the same type the referenced List is expecting.
            e
        )
    );
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            // TODO(steving) After assertion that this Expr is ACTUALLY an Integer (once that's supported in Claro) then
            // TODO(steving) this should be able to drop the downcast from Double -> Integer.
            "%s.set((int) %s, %s);\n",
            this.getChildren().get(0).generateJavaSourceOutput(scopedHeap),
            this.getChildren().get(1).generateJavaSourceOutput(scopedHeap),
            this.getChildren().get(2).generateJavaSourceOutput(scopedHeap)
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Put the computed value of this specified List element directly into ArrayList in the heap.
    // TODO(steving) Determine the type in a type-safe way deferring to the ListExpr node.
    ((ArrayList<Object>) this.getChildren().get(0).generateInterpretedOutput(scopedHeap))
        .set(
            (int) this.getChildren().get(1).generateInterpretedOutput(scopedHeap),
            this.getChildren().get(2).generateInterpretedOutput(scopedHeap)
        );
    return null;
  }
}
