package com.claro.examples.calculator_example.intermediate_representation;

final public class FalseTerm extends Term {
  private final static boolean VALUE = false;

  public boolean getValue() {
    return VALUE;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder("false");
  }
}
