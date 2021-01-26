package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

public class InputExpr extends Expr {

  private final String prompt;

  public InputExpr(String prompt) {
    super(ImmutableList.of());
    this.prompt = prompt;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(String.format("promptUserInput(\"%s\")", this.prompt));
  }
}
