package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.google.common.collect.ImmutableList;

public class PrintStmt extends Stmt {

  public PrintStmt(Expr e) {
    super(ImmutableList.of(e));
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // TODO(steving) For now, everything is printable since we're just plain ol deferring to Java to print objects at
    // TODO(steving) the worst case. Instead, in the future, probably require that you only print Printable things or something.
    // Make sure that the encapsulated Expr does its on type validation on itself even though Print itself has no
    // constraints to impart on it.
    ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    String expr_java_source = ((Expr) this.getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap).toString();
    return GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                "System.out.println(%s);\n",
                expr_java_source
            )
        )
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    System.out.println(this.getChildren().get(0).generateInterpretedOutput(scopedHeap));
    return null;
  }
}
