package com.claro.intermediate_representation.expressions.term;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;

import java.util.function.Supplier;

final public class TrueTerm extends Term {
  private final static boolean VALUE = true;

  public TrueTerm(Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(currentLine, currentLineNumber, startCol, endCol);
  }

  public boolean getValue() {
    return VALUE;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap unusedScopedHeap) {
    return Types.BOOLEAN;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    // Let's not depend on Java to autobox (although sometimes we can depend on Java to auto-unbox for boolean arithmetic).
    return new StringBuilder("Boolean.TRUE");
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return true;
  }
}
