package com.claro.examples.calculator_example.intermediate_representation;

public class InputTerm extends Term {

  private final String prompt;

  public InputTerm(String prompt) {
    this.prompt = prompt;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(String.format("promptUserInput(\"%s\")", this.prompt));
  }
}
