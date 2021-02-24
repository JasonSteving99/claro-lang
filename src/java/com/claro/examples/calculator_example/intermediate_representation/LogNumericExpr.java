package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableList;

public class LogNumericExpr extends NumericExpr {

  // TODO(steving) This should only accept other NumericExpr args. Need to update the grammar.
  public LogNumericExpr(Expr arg, Expr log_base) {
    super(ImmutableList.of(arg, log_base));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            "(Math.log(%s) / Math.log(%s))",
            this.getChildren().get(0).generateJavaSourceOutput(scopedHeap),
            this.getChildren().get(1).generateJavaSourceOutput(scopedHeap)
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return
        Math.log((double) this.getChildren().get(0).generateInterpretedOutput(scopedHeap)) /
        Math.log((double) this.getChildren().get(1).generateInterpretedOutput(scopedHeap));
  }
}
