package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.BaseType;
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
  protected Type getValidatedExprType() {
    BaseType type;
    if (getChildren().isEmpty()) {
      // TODO(steving) What's the type of a List that was defined as `l = []` when later they could equivalently say
      // TODO(steving) `l.add(1)` or `l.add(1.0)` or `l.add("one")`?...Maybe instead we should require that for the
      // TODO(steving) empty list you have to say something like `l: [int] = []` on initial declaration instead?
      type = List
    } else {
      type =
    }
  }

  // TODO(steving) Re-implement this in a standardized way instead of this hacky one-off way.
  public String getJavaSourceType() {
    if (getChildren().isEmpty()) {
      return "Object";
    }
    Node firstChild = getChildren().get(0);
    if (firstChild instanceof ListExpr) {
      return String.format("ClaroList<%s>", ((ListExpr) firstChild).getJavaSourceType());
    } else {
      // TODO(steving) This needs to defer instead to the actual type of the type known by the Expr in the initializer list.
      return "Integer";
    }
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
