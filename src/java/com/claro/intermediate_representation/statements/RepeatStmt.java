package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;

public class RepeatStmt extends Stmt {
  private final Expr expr;
  private final StmtListNode stmtListNode;

  public RepeatStmt(Expr expr, StmtListNode stmtListNode) {
    super(ImmutableList.of());
    this.expr = expr;
    this.stmtListNode = stmtListNode;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    this.expr.assertExpectedExprType(scopedHeap, Types.INTEGER);

    // Since we don't know whether or not the repeat-loop body will actually execute, we won't be able to trigger branch
    // inspection on var initialization.
    scopedHeap.observeNewScope(false);

    boolean original_withinLoopingConstructBody = InternalStaticStateUtil.LoopingConstructs_withinLoopingConstructBody;
    InternalStaticStateUtil.LoopingConstructs_withinLoopingConstructBody = true;

    this.stmtListNode.assertExpectedExprTypes(scopedHeap);

    InternalStaticStateUtil.LoopingConstructs_withinLoopingConstructBody = original_withinLoopingConstructBody;

    scopedHeap.exitCurrObservedScope(false);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res =
        GeneratedJavaSource.forJavaSourceBody(new StringBuilder("for (int $repeatCounter = 0; $repeatCounter < "));
    res = res.createMerged(this.expr.generateJavaSourceOutput(scopedHeap));
    res.javaSourceBody().append("; ++$repeatCounter) {\n");
    res = res.createMerged(this.stmtListNode.generateJavaSourceOutput(scopedHeap));
    res.javaSourceBody().append("}\n");
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Eventually need to impl repeat when I come back to adding support for the interpreted backend.
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support `repeat(...) {...}` in the interpreted backend just yet!");
  }
}
