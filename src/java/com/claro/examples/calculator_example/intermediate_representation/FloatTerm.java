package com.claro.examples.calculator_example.intermediate_representation;

final public class FloatTerm extends Term {
  private final double value;

  public FloatTerm(double value) {
    super();
    this.value = value;
  }

  public double getValue() {
    return value;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder().append(value);
  }
}
