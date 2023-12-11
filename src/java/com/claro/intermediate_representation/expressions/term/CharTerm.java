package com.claro.intermediate_representation.expressions.term;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;

import java.util.function.Supplier;

public class CharTerm extends Term {
  private final Character value;

  public CharTerm(Character value, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(currentLine, currentLineNumber, startCol, endCol);
    this.value = value;
  }

  public Character getValue() {
    return value;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap unusedScopedHeap) {
    return Types.CHAR;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(String.format("'%s'", value));
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.getValue();
  }
}
