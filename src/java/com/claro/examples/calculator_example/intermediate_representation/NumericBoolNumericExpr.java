package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

public class NumericBoolNumericExpr extends NumericExpr {

  // TODO(steving) This should only accept other BoolExpr arg. Need to update the grammar.
  public NumericBoolNumericExpr(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(
        String.format(
            "(%s ? 1 : 0)",
            this.getChildren().get(0).generateJavaSourceOutput()
        )
    );
  }
}
