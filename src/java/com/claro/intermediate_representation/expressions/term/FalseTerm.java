package com.claro.intermediate_representation.expressions.term;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;

final public class FalseTerm extends Term {
  private final static boolean VALUE = false;

  public boolean getValue() {
    return VALUE;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap unusedScopedHeap) {
    return Types.BOOLEAN;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder("false");
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return false;
  }
}
