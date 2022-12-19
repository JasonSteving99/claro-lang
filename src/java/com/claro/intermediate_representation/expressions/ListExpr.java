package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.Node;
import com.claro.intermediate_representation.types.*;
import com.claro.intermediate_representation.types.impls.builtins_impls.collections.ClaroList;
import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ListExpr extends Expr {

  private Optional<Type> emptyListValueType;
  private final ImmutableList<Expr> initializerArgExprsList;

  // This type is only available after the type validation phase.
  private Type validatedListType;

  public ListExpr(ImmutableList<Expr> listInitializerArgsList, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.emptyListValueType = Optional.empty();
    this.initializerArgExprsList = listInitializerArgsList;
  }

  // When the grammar finds an empty list, it'll accept whatever type is asserted upon it by its surrounding context.
  public ListExpr(Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.emptyListValueType = Optional.of(Types.UNDECIDED);
    this.initializerArgExprsList = ImmutableList.of();
  }

  public ListExpr(Type emptyListValueType, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.emptyListValueType = Optional.of(emptyListValueType);
    this.initializerArgExprsList = ImmutableList.of();
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Type listType;
    if (this.initializerArgExprsList.isEmpty()) {
      // If this empty list was incorrectly used in a context that does not statically specify a type at compile-time
      // such as in the case of the invalid statement `var l = [];` then throw because the type of the empty list is
      // undecidable. Remember that in this situation, Claro demands that the types of values held by variables are
      // known unambiguously at the end of the line on which their declaration takes place.
      if (emptyListValueType.get().baseType().equals(BaseType.UNDECIDED)) {
        throw ClaroTypeException.forUndecidedTypeLeakEmptyListInitialization();
      }
      // The type of this empty list is known simply by the type that it was asserted to be within the statement context.
      listType = emptyListValueType.get();
    } else {
      Type listValuesType = this.validatedListType == null
                            ? this.initializerArgExprsList.get(0).getValidatedExprType(scopedHeap)
                            : this.validatedListType.parameterizedTypeArgs().get(Types.ListType.PARAMETERIZED_TYPE_KEY);
      // Need to assert that all values in the list are of the same type.
      for (Node initialListValue : this.initializerArgExprsList) {
        ((Expr) initialListValue).assertExpectedExprType(scopedHeap, listValuesType);
      }
      listType = Types.ListType.forValueType(listValuesType);
    }
    this.validatedListType = listType;
    return listType;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    expectedExprType = TypeProvider.Util.maybeDereferenceAliasSelfReference(expectedExprType, scopedHeap);
    // Definitely have a list here, the user can't lie and call it something else. Early check here before type
    // inference only in the case of an empty initializer since we'll give a better error message in the non-empty
    // case if waiting until after inference.
    if (!expectedExprType.baseType().equals(BaseType.LIST)) {
      logTypeError(new ClaroTypeException(BaseType.LIST, expectedExprType));
      return;
    }
    this.validatedListType = expectedExprType;
    if (initializerArgExprsList.isEmpty()) {
      // For empty lists, the type assertion is actually used as the injection of context of this list's assumed type.
      this.emptyListValueType = Optional.of(validatedListType);
    } else {
      super.assertExpectedExprType(scopedHeap, validatedListType);
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    AtomicReference<GeneratedJavaSource> initializerValsGenJavaSource =
        new AtomicReference<>(GeneratedJavaSource.forJavaSourceBody(new StringBuilder()));

    String listFormatString = "ClaroList.initializeList(%s%s)";
    String initializerArgs;
    if (initializerArgExprsList.isEmpty()) {
      initializerArgs = "";
    } else {
      initializerArgs =
          this.initializerArgExprsList.stream()
              .map(expr -> {
                GeneratedJavaSource curr = expr.generateJavaSourceOutput(scopedHeap);
                String currJavaSource = curr.javaSourceBody().toString();
                curr.javaSourceBody().setLength(0);
                initializerValsGenJavaSource.set(initializerValsGenJavaSource.get().createMerged(curr));
                return currJavaSource;
              })
              .collect(Collectors.joining(", ", ", ", ""));
    }
    return GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder(
                String.format(listFormatString, this.validatedListType.getJavaSourceClaroType(), initializerArgs)))
        .createMerged(initializerValsGenJavaSource.get());
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ClaroList.initializeList(
        this.validatedListType,
        this.initializerArgExprsList.stream()
            .map(expr -> expr.generateInterpretedOutput(scopedHeap))
            .toArray()
    );
  }
}
