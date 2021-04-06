package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;

public class ParenthesizedExpr extends Expr {

  public ParenthesizedExpr(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  protected Type getValidatedExprType(ScopedHeap scopedHeap) {
    return ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap);
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            "(%s)",
            this.getChildren().get(0).generateJavaSourceOutput(scopedHeap)
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.getChildren().get(0).generateInterpretedOutput(scopedHeap);
  }
}
