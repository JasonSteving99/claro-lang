package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;

public class ListSubscriptExpr extends Expr {

  public ListSubscriptExpr(Node listNode, Expr expr) {
    // TODO(steving) TWO type checks needed: first that the given listNode has enough levels of nesting for this current
    // TODO(steving) subscript level, second that the given Expr is actually an integer.
    super(ImmutableList.of(listNode, expr));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return getChildren().get(0)
        .generateJavaSourceOutput(scopedHeap)
        // TODO(steving) There needs to instead be an explicit type check here that this value is an integer.
        .append(String.format(".get((int)%s)", getChildren().get(1).generateJavaSourceOutput(scopedHeap)));
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((ArrayList<Double>) getChildren().get(0).generateInterpretedOutput(scopedHeap))
        .get(
            // TODO(steving) There needs to instead be an explicit type check here that this value is an integer.
            (int) getChildren().get(1).generateInterpretedOutput(scopedHeap));
  }
}
