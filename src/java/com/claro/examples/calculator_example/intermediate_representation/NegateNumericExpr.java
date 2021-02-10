package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;

public class NegateNumericExpr extends NumericExpr {

  // TODO(steving) This should only accept other NumericExpr args. Need to update the grammar.
  public NegateNumericExpr(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(
        String.format(
            "(-%s)",
            this.getChildren().get(0).generateJavaSourceOutput()
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(HashMap<String, Object> heap) {
    return -((double) this.getChildren().get(0).generateInterpretedOutput(heap));
  }
}
