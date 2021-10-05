package com.claro.intermediate_representation.expressions.term;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;

public abstract class Term extends Expr {
  public Term() {
    // Terms are the bottom of the grammar. No more children.
    super(ImmutableList.of());
  }

  // Override this method for Terms that actually need to do something with this ScopedHeap.
  @Override
  public abstract Type getValidatedExprType(ScopedHeap unusedScopedHeap);
}