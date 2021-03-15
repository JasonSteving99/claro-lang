package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;

final public class FloatTerm extends Term {
  private final Double value;

  public FloatTerm(Double value) {
    super();
    this.value = value;
  }

  public double getValue() {
    return value;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return new StringBuilder().append(this.value);
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.value;
  }
}
