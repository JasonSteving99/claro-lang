package com.claro.intermediate_representation.expressions.bool;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.function.Supplier;

public class EqualsBoolExpr extends BoolExpr {

  public EqualsBoolExpr(Expr lhs, Expr rhs, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(lhs, rhs), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  protected ImmutableSet<Type> getSupportedOperandTypes() {
    // The (ugly) contract here is that returning this empty set means that we'll accept any type, so long as both
    // operands are of the same type.
    return ImmutableSet.of();
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    return super.getValidatedExprType(scopedHeap);
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            "%s.equals(%s)",
            ((Expr) this.getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap),
            ((Expr) this.getChildren().get(1)).generateJavaSourceBodyOutput(scopedHeap)
        )
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.getChildren().get(0).generateInterpretedOutput(scopedHeap)
        .equals(
            this.getChildren().get(1).generateInterpretedOutput(scopedHeap));
  }
}
