package com.claro.intermediate_representation.expressions.numeric;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

import java.util.function.Supplier;

public class NumericBoolNumericExpr extends NumericExpr {

  // TODO(steving) This should only accept other BoolExpr arg. Need to update the grammar.
  public NumericBoolNumericExpr(Expr e, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(e), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    ((Expr) this.getChildren().get(0)).assertExpectedExprType(scopedHeap, Types.BOOLEAN);

    return Types.INTEGER;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = ((Expr) this.getChildren().get(0)).generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource ternary = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                "(%s ? 1 : 0)",
                res.javaSourceBody()
            )));
    // Already consumed the java source of the boolean expr.
    res.javaSourceBody().setLength(0);
    return res.createMerged(ternary);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((boolean) this.getChildren().get(0).generateInterpretedOutput(scopedHeap)) ? 1 : 0;
  }
}
