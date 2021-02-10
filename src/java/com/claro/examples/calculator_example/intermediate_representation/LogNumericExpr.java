package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;

public class LogNumericExpr extends NumericExpr {

  // TODO(steving) This should only accept other NumericExpr args. Need to update the grammar.
  public LogNumericExpr(Expr arg, Expr log_base) {
    super(ImmutableList.of(arg, log_base));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(
        String.format(
            "(Math.log(%s) / Math.log(%s))",
            this.getChildren().get(0).generateJavaSourceOutput(),
            this.getChildren().get(1).generateJavaSourceOutput()
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(HashMap<String, Object> heap) {
    return
      Math.log((double) this.getChildren().get(0).generateInterpretedOutput(heap)) /
      Math.log((double) this.getChildren().get(1).generateInterpretedOutput(heap));
  }
}
