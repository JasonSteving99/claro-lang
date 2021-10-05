package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.builtins_impls.collections.Collection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class CollectionSubscriptExpr extends Expr {

  private static final ImmutableSet<BaseType> SUPPORTED_EXPR_BASE_TYPES =
      ImmutableSet.of(
          BaseType.LIST,
          BaseType.TUPLE
      );

  public CollectionSubscriptExpr(Expr collectionNodeExpr, Expr expr) {
    super(ImmutableList.of(collectionNodeExpr, expr));
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Expr collectionExpr = (Expr) this.getChildren().get(0);
    collectionExpr.assertSupportedExprBaseType(scopedHeap, SUPPORTED_EXPR_BASE_TYPES);
    ((Expr) this.getChildren().get(1)).assertExpectedExprType(scopedHeap, Types.INTEGER);

    Type type = ((Types.Collection) collectionExpr.getValidatedExprType(scopedHeap)).getElementType();

    if (!this.acceptUndecided) {
      if (type.baseType().equals(BaseType.UNDECIDED)) {
        // We shouldn't be able to reach this because programmers should instead cast undecided values.
        throw ClaroTypeException.forUndecidedTypeLeak();
      }
    }

    return type;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return ((Expr) getChildren().get(0))
        .generateJavaSourceBodyOutput(scopedHeap)
        .append(String.format(".getElement(%s)", ((Expr) getChildren().get(1)).generateJavaSourceBodyOutput(scopedHeap)));
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((Collection) getChildren().get(0).generateInterpretedOutput(scopedHeap))
        .getElement((int) getChildren().get(1).generateInterpretedOutput(scopedHeap));
  }
}
