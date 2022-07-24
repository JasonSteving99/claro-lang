package com.claro.intermediate_representation.expressions.term;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;

import java.util.function.Supplier;

final public class FalseTerm extends Term {
  private final static boolean VALUE = false;

  public FalseTerm(Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
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
    return new StringBuilder().append("new Boolean(").append("false").append(")");
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return false;
  }
}
