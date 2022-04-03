package com.claro.intermediate_representation;

import com.claro.compiler_backends.interpreted.ScopedHeap;
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
  public abstract GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap);

  public abstract Object generateInterpretedOutput(ScopedHeap scopedHeap);

  @AutoValue
  protected abstract static class GeneratedJavaSource {
    public abstract StringBuilder javaSourceBody();

    public abstract Optional<StringBuilder> optionalStaticDefinitions();

    public abstract Optional<StringBuilder> optionalStaticPreambleStmts();

    public GeneratedJavaSource withNewJavaSourceBody(StringBuilder javaSourceBody) {
      return new AutoValue_Node_GeneratedJavaSource(
          javaSourceBody, optionalStaticDefinitions(), optionalStaticPreambleStmts());
    }

    public static GeneratedJavaSource forJavaSourceBody(StringBuilder javaSourceBody) {
      return new AutoValue_Node_GeneratedJavaSource(javaSourceBody, Optional.empty(), Optional.empty());
    }

    public static GeneratedJavaSource forStaticDefinitions(StringBuilder staticDefinitions) {
      return new AutoValue_Node_GeneratedJavaSource(
          new StringBuilder(), Optional.of(staticDefinitions), Optional.empty());
    }

    public static GeneratedJavaSource forStaticDefinitionsAndPreamble(
        StringBuilder staticDefinitions, StringBuilder staticPreamble) {
      return new AutoValue_Node_GeneratedJavaSource(
          new StringBuilder(), Optional.of(staticDefinitions), Optional.of(staticPreamble));
    }

    public static GeneratedJavaSource create(
        StringBuilder javaSourceBody, StringBuilder staticDefinitions, StringBuilder staticPreamble) {
      return new AutoValue_Node_GeneratedJavaSource(
          javaSourceBody, Optional.of(staticDefinitions), Optional.of(staticPreamble));
    }

    public static GeneratedJavaSource create(
        StringBuilder javaSourceBody,
        Optional<StringBuilder> optionalStaticDefinitions,
        Optional<StringBuilder> optionalStaticPreamble) {
      return new AutoValue_Node_GeneratedJavaSource(javaSourceBody, optionalStaticDefinitions, optionalStaticPreamble);
    }

    @Override
    public String toString() {
      throw new UnsupportedOperationException("Internal Compiler Error: GeneratedJavaSource is internal only.");
    }
  }
}
