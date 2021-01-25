package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

public abstract class Node {
  private final ImmutableList<Node> children;

  public Node(ImmutableList<Node> children) {
    this.children = children;
  }

  public ImmutableList<Node> getChildren() {
    return children;
  }

  public StringBuilder generateTargetOutput(Target target) throws IllegalArgumentException {
    StringBuilder generatedOutput;
    switch (target) {
      case JAVA_SOURCE:
        generatedOutput = generateJavaSourceOutput();
        break;
      case INTERPRETED:
        generatedOutput = generateInterpretedOutput();
        break;
      default:
        throw new IllegalArgumentException("Unexpected Target: " + target);
    }
    return generatedOutput;
  }

  protected abstract StringBuilder generateJavaSourceOutput();

  protected StringBuilder generateInterpretedOutput() {
    // TODO(steving) Consider having all Node impls implement this, but for now, just default to do nothing.
    return new StringBuilder();
  }
}
