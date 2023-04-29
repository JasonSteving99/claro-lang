package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;

import java.util.function.Consumer;

public class PrintStmt extends Stmt {

  // This is part of a hack to collect the printed output from this program without a strict dep on the stdout
  // stream for the specific OS that the interpreter happens to be running on at the moment. That is important
  // only for the sake of the REPL-site since we need to print output on the browser rather than to the stdout
  // stream on the server-side.
  private final Consumer<String> printerDelegate;
  private Type validatedPrintedExprType;

  public PrintStmt(Expr e, Consumer<String> printerDelegate) {
    super(ImmutableList.of(e));
    this.printerDelegate = printerDelegate;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // TODO(steving) For now, everything is printable since we're just plain ol deferring to Java to print objects at
    // TODO(steving) the worst case. Instead, in the future, probably require that you only print Printable things or something.
    // Make sure that the encapsulated Expr does its on type validation on itself even though Print itself has no
    // constraints to impart on it.
    this.validatedPrintedExprType = ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource exprGeneratedJavaSource =
        ((Expr) this.getChildren().get(0)).generateJavaSourceOutput(scopedHeap);

    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                "System.out.println(%s);\n",
                this.validatedPrintedExprType.baseType().equals(BaseType.HTTP_CLIENT)
                ? String.format("\"%s\"", this.validatedPrintedExprType)
                : exprGeneratedJavaSource.javaSourceBody()
            )
        )
    );

    res = res.createMerged(
        GeneratedJavaSource.create(
            new StringBuilder(),
            exprGeneratedJavaSource.optionalStaticDefinitions(),
            exprGeneratedJavaSource.optionalStaticPreambleStmts()
        )
    );

    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    printerDelegate.accept(this.getChildren().get(0).generateInterpretedOutput(scopedHeap).toString());
    return null;
  }
}
