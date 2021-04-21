package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class NotEqualsBoolExpr extends BoolExpr {

  public NotEqualsBoolExpr(Expr lhs, Expr rhs) {
    super(ImmutableList.of(lhs, rhs));
  }

  @Override
  protected ImmutableSet<Type> getSupportedOperandTypes() {
    return ImmutableSet.of(Types.BOOLEAN);
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            "(%s != %s)",
            this.getChildren().get(0).generateJavaSourceOutput(scopedHeap),
            this.getChildren().get(1).generateJavaSourceOutput(scopedHeap)
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return !this.getChildren().get(0).generateInterpretedOutput(scopedHeap)
        .equals(
            this.getChildren().get(1).generateInterpretedOutput(scopedHeap));
  }
}
