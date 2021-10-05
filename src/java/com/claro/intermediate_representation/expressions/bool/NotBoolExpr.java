package com.claro.intermediate_representation.expressions.bool;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
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
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            "(!%s)",
            ((Expr) this.getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap)
        )
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return !((boolean) this.getChildren().get(0).generateInterpretedOutput(scopedHeap));
  }
}
