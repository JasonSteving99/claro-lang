package com.claro.intermediate_representation.expressions.term;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;

import java.util.function.Supplier;

public class LongTerm extends Term {
  private final Long value;

  public LongTerm(Long value, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(currentLine, currentLineNumber, startCol, endCol);
    this.value = value;
  }

  public Long getValue() {
    return value;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap unusedScopedHeap) {
    return Types.LONG;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(String.format("%sL", value));
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.getValue();
  }
}
