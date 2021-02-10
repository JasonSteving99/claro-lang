package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;

public class NotEqualsBoolExpr extends BoolExpr {

  public NotEqualsBoolExpr(Expr lhs, Expr rhs) {
    super(ImmutableList.of(lhs, rhs));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(
        String.format(
            "(%s != %s)",
            this.getChildren().get(0).generateJavaSourceOutput(),
            this.getChildren().get(1).generateJavaSourceOutput()
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(HashMap<String, Object> heap) {
    return !this.getChildren().get(0).generateInterpretedOutput(heap)
        .equals(
            this.getChildren().get(1).generateInterpretedOutput(heap));
  }
}
