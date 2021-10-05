package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;

public class ParenthesizedExpr extends Expr {

  public ParenthesizedExpr(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    return ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap);
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            "(%s)",
            ((Expr) this.getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap)
        )
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.getChildren().get(0).generateInterpretedOutput(scopedHeap);
  }
}
