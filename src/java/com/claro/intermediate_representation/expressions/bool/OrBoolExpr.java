package com.claro.intermediate_representation.expressions.bool;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.function.Supplier;

public class OrBoolExpr extends BoolExpr {

  public OrBoolExpr(Expr lhs, Expr rhs, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(lhs, rhs), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  protected ImmutableSet<Type> getSupportedOperandTypes() {
    return ImmutableSet.of(Types.BOOLEAN);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource lhsGeneratedJavaSource = this.getChildren().get(0).generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource rhsGeneratedJavaSource = this.getChildren().get(1).generateJavaSourceOutput(scopedHeap);

    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                "(%s || %s)",
                lhsGeneratedJavaSource.javaSourceBody(),
                rhsGeneratedJavaSource.javaSourceBody()
            )
        ));

    // Need to ensure that we pick up the static definitions from each child expr.
    res = res.createMerged(
        GeneratedJavaSource.create(
            // We've already picked up the javaSourceBody, so we don't need it again.
            new StringBuilder(),
            lhsGeneratedJavaSource.optionalStaticDefinitions(),
            lhsGeneratedJavaSource.optionalStaticPreambleStmts()
        )).createMerged(
        GeneratedJavaSource.create(
            // We've already picked up the javaSourceBody, so we don't need it again.
            new StringBuilder(),
            rhsGeneratedJavaSource.optionalStaticDefinitions(),
            rhsGeneratedJavaSource.optionalStaticPreambleStmts()
        ));

    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return
        (boolean) this.getChildren().get(0).generateInterpretedOutput(scopedHeap) ||
        (boolean) this.getChildren().get(1).generateInterpretedOutput(scopedHeap);
  }
}
