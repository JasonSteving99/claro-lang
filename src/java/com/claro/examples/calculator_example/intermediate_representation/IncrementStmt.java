package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableList;

// This class exists solely to have something that distinguishes the Stmt "i++;" from the Expr "i++". Finicky finicky.
public class IncrementStmt extends Stmt {

  public IncrementStmt(IncrementExpr incrementExpr) {
    super(ImmutableList.of(incrementExpr));
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
