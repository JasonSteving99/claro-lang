package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableList;

public class IfStmt extends Stmt {

  public IfStmt(Expr expr, StmtListNode stmtListNode) {
    super(ImmutableList.of(expr, stmtListNode));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder res = new StringBuilder();
    res.append(
        String.format(
            "if ( %s )",
            this.getChildren()
                .get(0)
                .generateJavaSourceOutput(scopedHeap)
                .toString()
        )
    );
    // We've now entered a new scope.
    scopedHeap.enterNewScope();
    // Do work in this new scope.
    res.append(
        String.format(
            " {\n%s\n}\n",
            this.getChildren()
                .get(1)
                .generateJavaSourceOutput(scopedHeap)
                .toString()
        )
    );
    // And we're now leaving this scope.
    scopedHeap.exitCurrScope();
    return res;
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    if ((boolean) this.getChildren().get(0).generateInterpretedOutput(scopedHeap)) {
      // We've now entered a new scope.
      scopedHeap.enterNewScope();
      // Do work in this new scope.
      this.getChildren().get(1).generateInterpretedOutput(scopedHeap);
      // And we're now leaving this scope.
      scopedHeap.exitCurrScope();
    }
    return null;
  }
}
