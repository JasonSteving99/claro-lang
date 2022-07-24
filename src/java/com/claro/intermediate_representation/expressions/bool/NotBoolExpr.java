package com.claro.intermediate_representation.expressions.bool;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.function.Supplier;

public class NotBoolExpr extends BoolExpr {

  public NotBoolExpr(Expr e, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(e), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  protected ImmutableSet<Type> getSupportedOperandTypes() {
    return ImmutableSet.of(Types.BOOLEAN);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource exprGeneratedJavaSource =
        ((Expr) this.getChildren().get(0)).generateJavaSourceOutput(scopedHeap);

    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                "(!%s)",
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
    return !((boolean) this.getChildren().get(0).generateInterpretedOutput(scopedHeap));
  }
}
