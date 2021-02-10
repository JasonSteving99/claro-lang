package com.claro.examples.calculator_example.intermediate_representation;

import java.util.HashMap;

final public class StringTerm extends Term {
  private final String value;

  public StringTerm(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(String.format("\"%s\"", value));
  }

  @Override
  protected Object generateInterpretedOutput(HashMap<String, Object> heap) {
    return this.getValue();
  }
}
