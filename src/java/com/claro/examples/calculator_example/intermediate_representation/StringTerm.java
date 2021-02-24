package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;

final public class StringTerm extends Term {
  private final String value;

  public StringTerm(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(String.format("\"%s\"", value));
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.getValue();
  }
}
