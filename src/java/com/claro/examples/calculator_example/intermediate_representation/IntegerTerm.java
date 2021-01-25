package com.claro.examples.calculator_example.intermediate_representation;

final public class IntegerTerm extends Term {
  private final int value;

  public IntegerTerm(int value) {
    super();
    this.value = value;
  }

  public int getValue() {
    return value;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder().append(value);
  }
}
