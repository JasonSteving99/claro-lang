package com.claro.intermediate_representation.expressions.term;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;

import java.util.function.Supplier;

final public class FloatTerm extends Term {
  private final Double value;

  public FloatTerm(Double value, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(currentLine, currentLineNumber, startCol, endCol);
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
    // Let's not depend on Java to autobox (although sometimes we can depend on Java to auto-unbox for arithmetic).
    return new StringBuilder().append("Double.valueOf(").append(this.value).append(")");
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.value;
  }
}
