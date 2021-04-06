package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

public abstract class BoolExpr extends Expr {
  // For now this class is left empty, though in the future it'll likely contain at least typing information.
  public BoolExpr(ImmutableList<Node> children) {
    super(children);
  }

  @Override
  protected final Type getValidatedExprType(ScopedHeap scopedHeap) {
    return Types.BOOLEAN;
  }
}
