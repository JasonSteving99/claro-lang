package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class NotBoolExpr extends BoolExpr {

  public NotBoolExpr(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  protected ImmutableSet<Type> getSupportedOperandTypes() {
    return ImmutableSet.of(Types.BOOLEAN);
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            "(!%s)",
            this.getChildren().get(0).generateJavaSourceOutput(scopedHeap)
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return !((boolean) this.getChildren().get(0).generateInterpretedOutput(scopedHeap));
  }
}
