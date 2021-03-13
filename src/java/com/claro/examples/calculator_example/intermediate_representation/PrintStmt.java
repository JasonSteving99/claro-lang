package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableList;

public class PrintStmt extends Stmt {

  public PrintStmt(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    String expr_java_source = this.getChildren().get(0).generateJavaSourceOutput(scopedHeap).toString();
    return new StringBuilder(
        String.format(
            "System.out.println(%s);\n",
            expr_java_source
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    System.out.println(this.getChildren().get(0).generateInterpretedOutput(scopedHeap));
    return null;
  }
}
