package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

public class WhileStmt extends Stmt {

  // Constructor for "if" and "else if" statements that do have a condition to check.
  public WhileStmt(Expr expr, StmtListNode stmtListNode) {
    super(ImmutableList.of(expr, stmtListNode));
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // TODO(steving) Implement some sort of compile-time detection of "obviously-true, expression detection" so that we
    // TODO(steving) could potentially add support for branch inspection for var initialization on while-loops as well.
    // TODO(steving) Very low priority, since it'd only be on some cases where things are obvious, like while(true).
    // TODO(steving) This would already be difficult with something as simple as `var i = 1; while (i < 2) {...}`.
    ((Expr) this.getChildren().get(0)).assertExpectedExprType(scopedHeap, Types.BOOLEAN);

    // Since we don't know whether or not the while-loop body will actually execute, we won't be able to trigger branch
    // inspection on var initialization.
    scopedHeap.observeNewScope(false);
    ((StmtListNode) this.getChildren().get(1)).assertExpectedExprTypes(scopedHeap);
    scopedHeap.exitCurrObservedScope(false);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    String conditionJavaSource = ((Expr) getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap).toString();

    // Body of the while
    scopedHeap.enterNewScope();
    GeneratedJavaSource bodyStmtListJavaSource = getChildren().get(1).generateJavaSourceOutput(scopedHeap);
    scopedHeap.exitCurrScope();

    return bodyStmtListJavaSource.withNewJavaSourceBody(
        new StringBuilder(
            String.format("while ( %s ) {\n%s\n}\n", conditionJavaSource, bodyStmtListJavaSource.javaSourceBody()))
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
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
