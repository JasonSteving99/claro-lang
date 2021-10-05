package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;

public class LenExpr extends Expr {
  public LenExpr(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // TODO(steving) Add support for len of Array/Tuple as well.
    ((Expr) this.getChildren().get(0)).assertExpectedBaseType(scopedHeap, BaseType.LIST);

    return Types.INTEGER;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return ((Expr) this.getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap).append(".length()");
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((ArrayList) this.getChildren().get(0).generateInterpretedOutput(scopedHeap)).size();
  }
}
