package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.IncrementExpr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

// This class exists solely to have something that distinguishes the Stmt "i++;" from the Expr "i++". Finicky finicky.
public class IncrementStmt extends Stmt {

  public IncrementStmt(IncrementExpr incrementExpr) {
    super(ImmutableList.of(incrementExpr));
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    ((Expr) this.getChildren().get(0)).assertExpectedExprType(scopedHeap, Types.INTEGER);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return GeneratedJavaSource.forJavaSourceBody(
        ((Expr) getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap).append(";"));
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    getChildren().get(0).generateInterpretedOutput(scopedHeap);
    return null;
  }
}
