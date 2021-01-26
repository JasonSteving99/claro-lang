package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

public class AndBoolExpr extends BoolExpr {

  public AndBoolExpr(Expr lhs, Expr rhs) {
    super(ImmutableList.of(lhs, rhs));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(
        String.format(
            "(%s && %s)",
            this.getChildren().get(0).generateJavaSourceOutput(),
            this.getChildren().get(1).generateJavaSourceOutput()
        )
    );
  }
}
