package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

public class ListExpr extends Expr {

  private final Optional<Type> emptyListValueType;

  public ListExpr(ImmutableList<Node> listInitializerArgsList) {
    super(listInitializerArgsList);
    this.emptyListValueType = Optional.empty();
  }

  // TODO(steving) Drop this constructor option. We need the empty list type to be set. Use constructor below.
  public ListExpr() {
    super(ImmutableList.of());
    this.emptyListValueType = Optional.empty();
  }

  public ListExpr(Type emptyListValueType) {
    super(ImmutableList.of());
    this.emptyListValueType = Optional.of(emptyListValueType);
  }

  @Override
  protected Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (getChildren().isEmpty()) {
      // TODO(steving) What's the type of a List that was defined as `l = []` when later they could equivalently say
      // TODO(steving) `l.add(1)` or `l.add(1.0)` or `l.add("one")`?...Maybe instead we should require that for the
      // TODO(steving) empty list you have to say something like `l: [int] = []` on initial declaration instead?
      throw new UnsupportedOperationException(
          "TODO(steving) Empty list type has to be special-cased into an initialization stmt in order to know the " +
          "type on the line of use.");
    } else {
      Type listValuesType = ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap);
      // Need to assert that all values in the list are of the same type.
      for (Node initialListValue : this.getChildren()) {
        ((Expr) initialListValue).assertExpectedExprType(scopedHeap, listValuesType);
      }
      return Types.ListType.forValueType(listValuesType);
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
