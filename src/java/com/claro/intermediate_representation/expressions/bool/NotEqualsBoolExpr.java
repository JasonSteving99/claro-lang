package com.claro.intermediate_representation.expressions.bool;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class NotEqualsBoolExpr extends BoolExpr {

  private boolean primitives;

  public NotEqualsBoolExpr(Expr lhs, Expr rhs) {
    super(ImmutableList.of(lhs, rhs));
  }

  @Override
  protected ImmutableSet<Type> getSupportedOperandTypes() {
    // The (ugly) contract here is that returning this empty set means that we'll accept any type, so long as both
    // operands are of the same type.
    return ImmutableSet.of();
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Before letting the superclass do its thing, I need to hold onto the type of one of the exprs so that later we can
    // decide whether to use '==' or '.equals()'.
    this.primitives = ImmutableSet.of(Types.BOOLEAN, Types.INTEGER, Types.FLOAT)
        .contains(((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap));
    return super.getValidatedExprType(scopedHeap);
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            // All types except for JAVA primitives need to be compared with .equals().
            this.primitives ? "%s != %s" : "!(%s.equals(%s))",
            ((Expr) this.getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap),
            ((Expr) this.getChildren().get(1)).generateJavaSourceBodyOutput(scopedHeap)
        )
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return !this.getChildren().get(0).generateInterpretedOutput(scopedHeap)
        .equals(
            this.getChildren().get(1).generateInterpretedOutput(scopedHeap));
  }
}
