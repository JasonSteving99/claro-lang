package com.claro.intermediate_representation;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public abstract class Node {
  public Optional<Object> opaqueData_workaroundToAvoidCircularDepsCausedByExprToStmtBuildTargets = Optional.empty();

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

  // TODO(steving) Rip the interpreter out of the compiler. This is defunct and unsupported and just extra noise.
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    throw new RuntimeException("Internal Compiler Error! The interpreted backend is no longer supported!");
  }

  @AutoValue
  public abstract static class GeneratedJavaSource {
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

    /**
     * Creates a new {@link GeneratedJavaSource} instance representing the merging of this instance and {@param other}.
     *
     * @param other The other instance to merge into this one.
     * @return a new {@link GeneratedJavaSource} instance.
     */
    public GeneratedJavaSource createMerged(GeneratedJavaSource other) {
      StringBuilder javaSourceBuilder = new StringBuilder(this.javaSourceBody());
      StringBuilder staticDefinitionsBuilder =
          new StringBuilder(this.optionalStaticDefinitions().orElse(new StringBuilder()));
      StringBuilder staticPreambleBuilder =
          new StringBuilder(this.optionalStaticPreambleStmts().orElse(new StringBuilder()));
      AtomicReference<Boolean> useStaticDefinitionsBuilder =
          new AtomicReference<>(this.optionalStaticDefinitions().isPresent());
      AtomicReference<Boolean> useStaticPreambleBuilder =
          new AtomicReference<>(this.optionalStaticPreambleStmts().isPresent());

      javaSourceBuilder.append(other.javaSourceBody());
      other.optionalStaticDefinitions().ifPresent(sb -> {
        useStaticDefinitionsBuilder.set(true);
        staticDefinitionsBuilder.append(sb);
      });
      other.optionalStaticPreambleStmts().ifPresent(sb -> {
        useStaticPreambleBuilder.set(true);
        staticPreambleBuilder.append(sb);
      });

      return GeneratedJavaSource.create(
          javaSourceBuilder,
          useStaticDefinitionsBuilder.get() ? Optional.of(staticDefinitionsBuilder) : Optional.empty(),
          useStaticPreambleBuilder.get() ? Optional.of(staticPreambleBuilder) : Optional.empty()
      );
    }

    @Override
    public String toString() {
      throw new UnsupportedOperationException("Internal Compiler Error: GeneratedJavaSource is internal only.");
    }
  }
}
