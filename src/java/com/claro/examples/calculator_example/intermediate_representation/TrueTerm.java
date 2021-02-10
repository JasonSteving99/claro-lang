package com.claro.examples.calculator_example.intermediate_representation;

import java.util.HashMap;

final public class TrueTerm extends Term {
  private final static boolean VALUE = true;

  public boolean getValue() {
    return VALUE;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder("true");
  }

  @Override
  protected Object generateInterpretedOutput(HashMap<String, Object> heap) {
    return true;
  }
}
