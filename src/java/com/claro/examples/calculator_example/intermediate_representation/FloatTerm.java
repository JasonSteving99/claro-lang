package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;

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
  protected Type getValidatedExprType(ScopedHeap unusedScopedHeap) {
    return Types.FLOAT;
  }

  @Override
  protected StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder().append(this.value);
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.value;
  }
}
