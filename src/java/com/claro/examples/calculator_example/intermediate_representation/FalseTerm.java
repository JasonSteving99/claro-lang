package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;

final public class FalseTerm extends Term {
  private final static boolean VALUE = false;

  public boolean getValue() {
    return VALUE;
  }

  @Override
  protected Type getValidatedExprType(ScopedHeap unusedScopedHeap) {
    return Types.BOOLEAN;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return new StringBuilder("false");
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return false;
  }
}
