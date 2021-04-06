package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;

final public class TrueTerm extends Term {
  private final static boolean VALUE = true;

  public boolean getValue() {
    return VALUE;
  }

  @Override
  protected Type getValidatedExprType() {
    return Types.BOOLEAN;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return new StringBuilder("true");
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return true;
  }
}
