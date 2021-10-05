package com.claro.examples.calculator_example.intermediate_representation.expressions.term;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;

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
  public Type getValidatedExprType(ScopedHeap unusedScopedHeap) {
    return Types.INTEGER;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder().append(this.value);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.getValue();
  }
}
