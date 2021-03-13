package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class ListExpr extends Expr {

  public ListExpr() {
    super(ImmutableList.of());
  }

  public ListExpr(ImmutableList<Node> listInitializerArgsList) {
    super(listInitializerArgsList);
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Simply for parity with the interpreted implementation, this is how we'll get this ArrayList.
    String listFormatString = "initializeList(%s)";
    String formatArg;
    if (getChildren().isEmpty()) {
      formatArg = "";
    } else {
      formatArg =
          this.getChildren().stream()
              .map(expr -> expr.generateJavaSourceOutput(scopedHeap))
              .collect(Collectors.joining(", ")
              );
    }
    return new StringBuilder(String.format(listFormatString, formatArg));
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.getChildren().stream()
        .map(expr -> expr.generateInterpretedOutput(scopedHeap))
        .collect(Collectors.toCollection(ArrayList::new));
  }
}
