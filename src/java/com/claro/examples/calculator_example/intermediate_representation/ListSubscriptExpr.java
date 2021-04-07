package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.BaseType;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;

public class ListSubscriptExpr extends Expr {

  public ListSubscriptExpr(Expr listNodeExpr, Expr expr) {
    super(ImmutableList.of(listNodeExpr, expr));
  }

  @Override
  protected Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Expr listExpr = (Expr) this.getChildren().get(0);
    listExpr.assertExpectedBaseType(scopedHeap, BaseType.LIST);
    ((Expr) this.getChildren().get(1)).assertExpectedExprType(scopedHeap, Types.INTEGER);

    return listExpr.getValidatedExprType(scopedHeap).parameterizedTypeArgs().get("values");
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return getChildren().get(0)
        .generateJavaSourceOutput(scopedHeap)
        .append(String.format(".get(%s)", getChildren().get(1).generateJavaSourceOutput(scopedHeap)));
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((ArrayList<?>) getChildren().get(0).generateInterpretedOutput(scopedHeap))
        .get((int) getChildren().get(1).generateInterpretedOutput(scopedHeap));
  }
}
