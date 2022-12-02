package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

public class IsInputReadyExpr extends Expr {

  public IsInputReadyExpr() {
    super(ImmutableList.of(), () -> null, -1, -1, -1);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    return Types.BOOLEAN;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder("UserInput.INPUT_SCANNER.hasNextLine()");
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return InputExpr.INPUT_SCANNER.hasNextLine();
  }
}

