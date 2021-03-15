package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;

final public class IntegerTerm extends Term {
  private final Integer value;

  public IntegerTerm(Integer value) {
    super();
    this.value = value;
  }

  public int getValue() {
    return this.value;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return new StringBuilder().append(this.value);
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.getValue();
  }
}
