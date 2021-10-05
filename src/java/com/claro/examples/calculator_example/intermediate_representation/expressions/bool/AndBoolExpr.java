package com.claro.examples.calculator_example.intermediate_representation.expressions.bool;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.expressions.Expr;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class AndBoolExpr extends BoolExpr {

  public AndBoolExpr(Expr lhs, Expr rhs) {
    super(ImmutableList.of(lhs, rhs));
  }

  @Override
  protected ImmutableSet<Type> getSupportedOperandTypes() {
    return ImmutableSet.of(Types.BOOLEAN);
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            "(%s && %s)",
            ((Expr) this.getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap),
            ((Expr) this.getChildren().get(1)).generateJavaSourceBodyOutput(scopedHeap)
        )
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return
        (boolean) this.getChildren().get(0).generateInterpretedOutput(scopedHeap) &&
        (boolean) this.getChildren().get(1).generateInterpretedOutput(scopedHeap);
  }
}
