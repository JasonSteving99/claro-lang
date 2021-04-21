package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.BaseType;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;

public class LenExpr extends Expr {
  public LenExpr(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  protected Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // TODO(steving) Add support for len of Array/Tuple as well.
    ((Expr) this.getChildren().get(0)).assertExpectedBaseType(scopedHeap, BaseType.LIST);

    return Types.INTEGER;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return this.getChildren().get(0).generateJavaSourceOutput(scopedHeap).append(".length()");
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((ArrayList) this.getChildren().get(0).generateInterpretedOutput(scopedHeap)).size();
  }
}
