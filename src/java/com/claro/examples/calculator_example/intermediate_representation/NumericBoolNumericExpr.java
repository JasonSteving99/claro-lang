package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableList;

public class NumericBoolNumericExpr extends NumericExpr {

  // TODO(steving) This should only accept other BoolExpr arg. Need to update the grammar.
  public NumericBoolNumericExpr(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            "(%s ? 1 : 0)",
            this.getChildren().get(0).generateJavaSourceOutput(scopedHeap)
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((boolean) this.getChildren().get(0).generateInterpretedOutput(scopedHeap)) ? 1.0 : 0.0;
  }
}
