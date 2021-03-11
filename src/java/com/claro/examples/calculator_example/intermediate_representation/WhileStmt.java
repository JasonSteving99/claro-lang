package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableList;

public class WhileStmt extends Stmt {

  // Constructor for "if" and "else if" statements that do have a condition to check.
  public WhileStmt(Expr expr, StmtListNode stmtListNode) {
    super(ImmutableList.of(expr, stmtListNode));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    String conditionJavaSource = getChildren().get(0).generateJavaSourceOutput(scopedHeap).toString();

    // Body of the while
    scopedHeap.enterNewScope();
    String bodyStmtListJavaSource = getChildren().get(1).generateJavaSourceOutput(scopedHeap).toString();
    scopedHeap.exitCurrScope();

    return new StringBuilder(
        String.format("while ( %s ) {\n%s\n}\n", conditionJavaSource, bodyStmtListJavaSource));
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // In order to make sure that the scopes are honored correctly but also that we don't
    // have to wastefully push and pop the same body scope repeatedly, we'll first check
    // the while loop's condition before the new scope is pushed and then do the rest of
    // the iterating inside a do-while after pushing a new scope. This simply allows us to
    // make sure that the loop condition isn't somehow attempting to depend on variables
    // declared only in the body, while also eliminating wasteful pushes/pops of scope stack
    // since on first pass through the body we'll have already checked that all the new vars
    // are actually initialized before access, therefore we don't need to clear the while body's
    // scope, since everything will be reinitialized by the code itself.
    Expr whileCondExpr = (Expr) getChildren().get(0);
    if ((boolean) whileCondExpr.generateInterpretedOutput(scopedHeap)) {
      scopedHeap.enterNewScope();
      do {
        getChildren().get(1).generateInterpretedOutput(scopedHeap);
      } while ((boolean) whileCondExpr.generateInterpretedOutput(scopedHeap));
      scopedHeap.exitCurrScope();
    }
    return null;
  }
}
