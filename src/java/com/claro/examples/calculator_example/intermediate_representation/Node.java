package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
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
    ScopedHeap scopedHeap = new ScopedHeap();
    scopedHeap.enterNewScope();
    return generateTargetOutput(target, scopedHeap);
  }

  public StringBuilder generateTargetOutput(Target target, ScopedHeap scopedHeap) throws IllegalArgumentException {
    StringBuilder generatedOutput;
    switch (target) {
      case JAVA_SOURCE:
        generatedOutput = generateJavaSourceOutput(scopedHeap);
        break;
      case REPL:
        // We can't check for unused identifiers in the REPL because we might just not yet have seen the instruction
        // where a given identifier will be used.
        scopedHeap.disableCheckUnused();
        generatedOutput = new StringBuilder().append(generateInterpretedOutput(scopedHeap));
        break;
      case INTERPRETED:
        generatedOutput = new StringBuilder().append(generateInterpretedOutput(scopedHeap));
        break;
      default:
        throw new IllegalArgumentException("Unexpected Target: " + target);
    }
    return generatedOutput;
  }

  // In this case the ScopedHeap is only getting used as a symbol table for lookups of whether the identifier is already
  // declared or not.
  protected abstract StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap);

  protected abstract Object generateInterpretedOutput(ScopedHeap scopedHeap);
}
