package com.claro.examples.calculator_example.intermediate_representation.expressions.numeric;

import com.claro.examples.calculator_example.intermediate_representation.Node;
import com.claro.examples.calculator_example.intermediate_representation.expressions.Expr;
import com.google.common.collect.ImmutableList;

public abstract class NumericExpr extends Expr {
  // For now this class is left empty, though in the future it'll likely contain at least typing information.
  public NumericExpr(ImmutableList<Node> children) {
    super(children);
  }
}
