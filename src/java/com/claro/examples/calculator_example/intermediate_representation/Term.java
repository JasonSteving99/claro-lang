package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

public abstract class Term extends Expr {
  public Term() {
    // Terms are the bottom of the grammar. No more children.
    super(ImmutableList.of());
  }
}
