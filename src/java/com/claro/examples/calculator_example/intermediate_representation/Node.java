package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.util.Optional;

public abstract class Node {
  private final ImmutableList<Node> children;

  public Node(ImmutableList<Node> children) {
    this.children = children;
  }

  public ImmutableList<Node> getChildren() {
    return children;
  }

  // In this case the ScopedHeap is only getting used as a symbol table for lookups of whether the identifier is already
  // declared or not.
  protected abstract GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap);

  protected abstract Object generateInterpretedOutput(ScopedHeap scopedHeap);

  @AutoValue
  abstract static class GeneratedJavaSource {
    abstract StringBuilder javaSourceBody();

    abstract Optional<StringBuilder> optionalStaticDefinitions();

    GeneratedJavaSource withNewJavaSourceBody(StringBuilder javaSourceBody) {
      return new AutoValue_Node_GeneratedJavaSource(javaSourceBody, optionalStaticDefinitions());
    }

    static GeneratedJavaSource forJavaSourceBody(StringBuilder javaSourceBody) {
      return new AutoValue_Node_GeneratedJavaSource(javaSourceBody, Optional.empty());
    }

    static GeneratedJavaSource forStaticDefinitions(StringBuilder staticDefinitions) {
      return new AutoValue_Node_GeneratedJavaSource(new StringBuilder(), Optional.of(staticDefinitions));
    }

    static GeneratedJavaSource create(StringBuilder javaSourceBody, StringBuilder staticDefinitions) {
      return new AutoValue_Node_GeneratedJavaSource(javaSourceBody, Optional.of(staticDefinitions));
    }
  }
}
