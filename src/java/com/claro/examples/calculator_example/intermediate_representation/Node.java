package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;

public abstract class Node {
  private final ImmutableList<Node> children;

  public Node(ImmutableList<Node> children) {
    this.children = children;
  }

  public ImmutableList<Node> getChildren() {
    return children;
  }

  public StringBuilder generateTargetOutput(Target target) throws IllegalArgumentException {
    return generateTargetOutput(target, new HashMap<>());
  }

  public StringBuilder generateTargetOutput(Target target, HashMap<String, Object> heap) throws IllegalArgumentException {
    StringBuilder generatedOutput;
    switch (target) {
      case JAVA_SOURCE:
        generatedOutput = generateJavaSourceOutput();
        break;
      case INTERPRETED:
        generatedOutput = new StringBuilder().append(generateInterpretedOutput(heap));
        break;
      default:
        throw new IllegalArgumentException("Unexpected Target: " + target);
    }
    return generatedOutput;
  }

  protected abstract StringBuilder generateJavaSourceOutput();

  protected abstract Object generateInterpretedOutput(HashMap<String, Object> heap);
}
