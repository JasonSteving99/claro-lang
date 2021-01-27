package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

public abstract class Stmt extends Node {
  public Stmt(ImmutableList<Node> children) {
    super(children);
  }
}
