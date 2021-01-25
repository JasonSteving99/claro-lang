package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

public abstract class Expr extends Node {
  public Expr(ImmutableList<Node> children) {
    super(children);
  }
}
