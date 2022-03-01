package com.claro.intermediate_representation.expressions.numeric;

import com.claro.intermediate_representation.Node;
import com.claro.intermediate_representation.expressions.Expr;
import com.google.common.collect.ImmutableList;

import java.util.function.Supplier;

public abstract class NumericExpr extends Expr {
  // For now this class is left empty, though in the future it'll likely contain at least typing information.
  public NumericExpr(ImmutableList<Node> children, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(children, currentLine, currentLineNumber, startCol, endCol);
  }
}
