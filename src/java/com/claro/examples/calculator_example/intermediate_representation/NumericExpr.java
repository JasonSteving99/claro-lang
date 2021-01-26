package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

public abstract class NumericExpr extends Expr {
  // For now this class is left empty, though in the future it'll likely contain at least typing information.
  public NumericExpr(ImmutableList<Node> children) {
    super(children);
  }
}
