package com.claro.examples.calculator_example.intermediate_representation.expressions.term;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;

final public class TrueTerm extends Term {
  private final static boolean VALUE = true;

  public boolean getValue() {
    return VALUE;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap unusedScopedHeap) {
    return Types.BOOLEAN;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder("true");
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return true;
  }
}
