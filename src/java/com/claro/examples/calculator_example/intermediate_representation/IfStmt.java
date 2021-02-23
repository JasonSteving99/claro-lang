package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;

public class IfStmt extends Stmt {

  public IfStmt(Expr expr, StmtListNode stmtListNode) {
    super(ImmutableList.of(expr, stmtListNode));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    StringBuilder res = new StringBuilder();
    res.append(
        String.format(
            "if ( %s ) {\n%s\n}\n",
            this.getChildren()
                .get(0)
                .generateJavaSourceOutput()
                .toString(),
            this.getChildren()
                .get(1)
                .generateJavaSourceOutput()
                .toString()
        )
    );
    return res;
  }

  @Override
  protected Object generateInterpretedOutput(HashMap<String, Object> heap) {
    if ((boolean) this.getChildren().get(0).generateInterpretedOutput(heap)) {
      this.getChildren().get(1).generateInterpretedOutput(heap);
    }
    return null;
  }
}
