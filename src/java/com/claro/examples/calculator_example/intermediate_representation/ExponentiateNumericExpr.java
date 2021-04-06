package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

public class ExponentiateNumericExpr extends NumericExpr {

  // TODO(steving) This should only accept other NumericExpr args. Need to update the grammar.
  public ExponentiateNumericExpr(Expr lhs, Expr rhs) {
    super(ImmutableList.of(lhs, rhs));
  }

  @Override
  protected Type getValidatedExprType(ScopedHeap scopedHeap) {
    return Types.DOUBLE;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            "Math.pow(%s, %s)",
            this.getChildren().get(0).generateJavaSourceOutput(scopedHeap),
            this.getChildren().get(1).generateJavaSourceOutput(scopedHeap)
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return Math.pow(
        (double) this.getChildren().get(0).generateInterpretedOutput(scopedHeap),
        (double) this.getChildren().get(1).generateInterpretedOutput(scopedHeap)
    );

  }
}
