package com.claro.intermediate_representation.expressions.term;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;

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
  public Type getValidatedExprType(ScopedHeap unusedScopedHeap) {
    return Types.FLOAT;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder().append(this.value);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.value;
  }
}
