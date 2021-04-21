package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

// This class exists solely to have something that distinguishes the Stmt "i--;" from the Expr "i--". Finicky finicky.
public class DecrementStmt extends Stmt {

  public DecrementStmt(DecrementExpr decrementExpr) {
    super(ImmutableList.of(decrementExpr));
  }

  @Override
  protected void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    ((Expr) this.getChildren().get(0)).assertExpectedExprType(scopedHeap, Types.INTEGER);
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return getChildren().get(0).generateJavaSourceOutput(scopedHeap).append(";");
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    getChildren().get(0).generateInterpretedOutput(scopedHeap);
    return null;
  }
}
