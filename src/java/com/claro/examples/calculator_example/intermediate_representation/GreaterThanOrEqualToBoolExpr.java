package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;

public class GreaterThanOrEqualToBoolExpr extends BoolExpr {

  public GreaterThanOrEqualToBoolExpr(Expr lhs, Expr rhs) {
    super(ImmutableList.of(lhs, rhs));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(
        String.format(
            "(%s >= %s)",
            this.getChildren().get(0).generateJavaSourceOutput(),
            this.getChildren().get(1).generateJavaSourceOutput()
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(HashMap<String, Object> heap) {
    return
        (double) this.getChildren().get(0).generateInterpretedOutput(heap) >=
        (double) this.getChildren().get(1).generateInterpretedOutput(heap);
  }
}
