package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.google.common.collect.ImmutableList;

public class PrintStmt extends Stmt {

  public PrintStmt(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  protected void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // TODO(steving) For now, everything is printable since we're just plain ol deferring to Java to print objects at
    // TODO(steving) the worst case. Instead, in the future, probably require that you only print Printable things or something.
    // Make sure that the encapsulated Expr does its on type validation on itself even though Print itself has no
    // constraints to impart on it.
    ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap);
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
