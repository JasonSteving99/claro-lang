package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;

import java.util.function.Supplier;

public class ParenthesizedExpr extends Expr {

  public ParenthesizedExpr(Expr e, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(e), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    return ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource exprGeneratedJavaSource =
        ((Expr) this.getChildren().get(0)).generateJavaSourceOutput(scopedHeap);

    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                "(%s)",
                exprGeneratedJavaSource.javaSourceBody()
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
    return this.getChildren().get(0).generateInterpretedOutput(scopedHeap);
  }
}
