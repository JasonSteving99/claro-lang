package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.builtins_impls.collections.Collection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.function.Supplier;

public class CollectionSubscriptExpr extends Expr {

  private boolean isString = false;

  private static final ImmutableSet<BaseType> SUPPORTED_EXPR_BASE_TYPES =
      ImmutableSet.of(
          BaseType.LIST,
          BaseType.TUPLE,
          BaseType.STRING,
          BaseType.MAP
      );

  public CollectionSubscriptExpr(Expr collectionNodeExpr, Expr expr, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(collectionNodeExpr, expr), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Expr collectionExpr = (Expr) this.getChildren().get(0);
    Type collectionExprType = collectionExpr.getValidatedExprType(scopedHeap);
    if (!SUPPORTED_EXPR_BASE_TYPES.contains(collectionExprType.baseType())) {
      // Make sure that this mismatch is logged on the offending Expr that was supposed to be a collection.
      collectionExpr.assertSupportedExprBaseType(scopedHeap, SUPPORTED_EXPR_BASE_TYPES);
      throw ClaroTypeException.forInvalidSubscriptForNonCollectionType(collectionExprType, SUPPORTED_EXPR_BASE_TYPES);
    }

    // Check that the subscript expr is a valid type.
    if (collectionExprType.baseType().equals(BaseType.MAP)) {
      ((Expr) this.getChildren().get(1)).assertExpectedExprType(
          scopedHeap,
          collectionExprType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS)
      );
    } else {
      ((Expr) this.getChildren().get(1)).assertExpectedExprType(scopedHeap, Types.INTEGER);
    }

    Type type;

    if (collectionExprType.baseType().equals(BaseType.STRING)) {
      type = Types.STRING;
      this.isString = true;
    } else if (collectionExprType.baseType().equals(BaseType.MAP)) {
      // TODO(steving) This should be modelled as a oneof<T|KeyErr> or something like that once the type system
      //  is mature enough for that. In this way, the return type would have to be checked with something like:
      //  var res = m["foo"];
      //  match(type(res)) {
      //    case string:
      //      print("Found what I was looking for: {res}");
      //    case KeyErr: # Where KeyErr is defined as an enum w/ multiple vals like KeyErr::Missing inside it.
      //      panic("Expected key wasn't found! Error: {res}");
      //  }
      type = collectionExprType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES);
    } else {
      type = ((Types.Collection) collectionExprType).getElementType();
    }

    if (!this.acceptUndecided) {
      if (type.baseType().equals(BaseType.UNDECIDED)) {
        // We shouldn't be able to reach this because programmers should instead cast undecided values.
        throw ClaroTypeException.forUndecidedTypeLeak();
      }
    }

    return type;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource exprGenJavaSource0 = getChildren().get(0).generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource exprGenJavaSource1 = getChildren().get(1).generateJavaSourceOutput(scopedHeap);

    GeneratedJavaSource subscriptExprGenJavaSource;
    if (this.isString) {
      subscriptExprGenJavaSource =
          GeneratedJavaSource.forJavaSourceBody(new StringBuilder("Character.toString("))
              .createMerged(
                  exprGenJavaSource0.createMerged(
                      GeneratedJavaSource.forJavaSourceBody(
                          new StringBuilder(
                              String.format(
                                  ".charAt(%s))",
                                  exprGenJavaSource1.javaSourceBody().toString()
                              ))))
              );
    } else {
      subscriptExprGenJavaSource =
          exprGenJavaSource0.createMerged(
              GeneratedJavaSource.forJavaSourceBody(
                  new StringBuilder(
                      String.format(
                          ".getElement(%s)",
                          exprGenJavaSource1.javaSourceBody().toString()
                      ))));
    }

    // We've already consumed the javaSourceBody, we're safe to clear it.
    exprGenJavaSource1.javaSourceBody().setLength(0);

    return subscriptExprGenJavaSource.createMerged(exprGenJavaSource1);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    if (this.isString) {
      int index = (int) getChildren().get(1).generateInterpretedOutput(scopedHeap);
      return ((String) getChildren().get(0).generateInterpretedOutput(scopedHeap))
          .substring(index, index + 1);
    }
    return ((Collection) getChildren().get(0).generateInterpretedOutput(scopedHeap))
        .getElement((int) getChildren().get(1).generateInterpretedOutput(scopedHeap));
  }
}
