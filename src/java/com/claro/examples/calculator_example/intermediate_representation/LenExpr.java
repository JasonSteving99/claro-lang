package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;

public class LenExpr extends Expr {
  public LenExpr(Expr e) {
    // TODO(steving) Assert that this Expr is an Iterable.
    super(ImmutableList.of(e));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return this.getChildren().get(0).generateJavaSourceOutput(scopedHeap).append(".length()");
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((ArrayList) this.getChildren().get(0).generateInterpretedOutput(scopedHeap)).size();
  }
}
