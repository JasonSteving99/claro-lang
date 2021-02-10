package com.claro.examples.calculator_example.intermediate_representation;

import java.util.HashMap;

final public class IntegerTerm extends Term {
  private final int value;

  public IntegerTerm(int value) {
    super();
    this.value = value;
  }

  public int getValue() {
    return this.value;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder().append(this.value);
  }

  @Override
  protected Object generateInterpretedOutput(HashMap<String, Object> heap) {
    // For now, simplify life and treat all numeric things in this language as doubles.
    return Double.valueOf(this.getValue());
  }
}
