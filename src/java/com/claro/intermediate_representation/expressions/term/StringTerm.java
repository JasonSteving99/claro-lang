package com.claro.intermediate_representation.expressions.term;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;

public class StringTerm extends Term {
  private final String value;

  public StringTerm(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap unusedScopedHeap) {
    return Types.STRING;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(String.format("\"%s\"", value));
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.getValue();
  }
}
