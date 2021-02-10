package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;

public class ParenthesizedExpr extends Expr {

  public ParenthesizedExpr(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(
        String.format(
            "(%s)",
            this.getChildren().get(0).generateJavaSourceOutput()
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(HashMap<String, Object> heap) {
    return this.getChildren().get(0).generateInterpretedOutput(heap);
  }
}
