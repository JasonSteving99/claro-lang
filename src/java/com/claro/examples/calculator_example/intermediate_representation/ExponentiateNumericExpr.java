package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;

public class ExponentiateNumericExpr extends NumericExpr {

  // TODO(steving) This should only accept other NumericExpr args. Need to update the grammar.
  public ExponentiateNumericExpr(Expr lhs, Expr rhs) {
    super(ImmutableList.of(lhs, rhs));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(
        String.format(
            "Math.pow(%s, %s)",
            this.getChildren().get(0).generateJavaSourceOutput(),
            this.getChildren().get(1).generateJavaSourceOutput()
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(HashMap<String, Object> heap) {
    return Math.pow(
        (double) this.getChildren().get(0).generateInterpretedOutput(heap),
        (double) this.getChildren().get(1).generateInterpretedOutput(heap)
    );

  }
}
