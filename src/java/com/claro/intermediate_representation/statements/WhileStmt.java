package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;

public class WhileStmt extends Stmt {

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

    boolean original_withinLoopingConstructBody = InternalStaticStateUtil.LoopingConstructs_withinLoopingConstructBody;
    InternalStaticStateUtil.LoopingConstructs_withinLoopingConstructBody = true;

    ((StmtListNode) this.getChildren().get(1)).assertExpectedExprTypes(scopedHeap);

    InternalStaticStateUtil.LoopingConstructs_withinLoopingConstructBody = original_withinLoopingConstructBody;

    scopedHeap.exitCurrObservedScope(false);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource conditionJavaSource = ((Expr) getChildren().get(0)).generateJavaSourceOutput(scopedHeap);

    // Body of the while
    scopedHeap.enterNewScope();
    GeneratedJavaSource bodyStmtListJavaSource = getChildren().get(1).generateJavaSourceOutput(scopedHeap);
    scopedHeap.exitCurrScope();

    GeneratedJavaSource resGenJavaSource =
        bodyStmtListJavaSource.withNewJavaSourceBody(
            new StringBuilder(
                String.format(
                    "while ( %s ) {\n%s\n}\n",
                    conditionJavaSource.javaSourceBody().toString(),
                    bodyStmtListJavaSource.javaSourceBody().toString()
                ))
        );
    conditionJavaSource.javaSourceBody().setLength(0);
    bodyStmtListJavaSource.javaSourceBody().setLength(0);

    return resGenJavaSource.createMerged(conditionJavaSource).createMerged(bodyStmtListJavaSource);
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
    Object maybeReturnValue = null;
    if ((boolean) whileCondExpr.generateInterpretedOutput(scopedHeap)) {
      scopedHeap.enterNewScope();
      do {
        maybeReturnValue = getChildren().get(1).generateInterpretedOutput(scopedHeap);

        if (maybeReturnValue != null) {
          // The last executed Stmt happened to be a ReturnStmt. We therefore need to break out
          // of this loop so that no more potential side effects happen when they shouldn't.
          break;
        }
      } while ((boolean) whileCondExpr.generateInterpretedOutput(scopedHeap));
      scopedHeap.exitCurrScope();
    }
    // This return value is probably `null` unless the last executed Stmt happened to be a ReturnStmt.
    return maybeReturnValue;
  }
}
