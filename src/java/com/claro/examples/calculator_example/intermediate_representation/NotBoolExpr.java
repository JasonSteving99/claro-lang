package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

public class NotBoolExpr extends BoolExpr {

  public NotBoolExpr(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(
        String.format(
            "(!%s)",
            this.getChildren().get(0).generateJavaSourceOutput()
        )
    );
  }
}
