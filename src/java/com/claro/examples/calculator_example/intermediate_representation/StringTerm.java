package com.claro.examples.calculator_example.intermediate_representation;

final public class StringTerm extends Term {
  private final String value;

  public StringTerm(String value) {
    super();
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(String.format("\"%s\"", value));
  }
}
