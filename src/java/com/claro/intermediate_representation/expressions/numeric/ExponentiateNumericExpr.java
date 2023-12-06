package com.claro.intermediate_representation.expressions.numeric;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.function.Supplier;

public class ExponentiateNumericExpr extends NumericExpr {

  private static final ImmutableSet<Type> SUPPORTED_EXPONENTIATE_OPERAND_TYPES =
      ImmutableSet.of(Types.INTEGER, Types.FLOAT);

  // TODO(steving) This should only accept other NumericExpr args. Need to update the grammar.
  public ExponentiateNumericExpr(Expr lhs, Expr rhs, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(lhs, rhs), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    ((Expr) this.getChildren().get(0)).assertSupportedExprType(scopedHeap, SUPPORTED_EXPONENTIATE_OPERAND_TYPES);
    ((Expr) this.getChildren().get(1)).assertSupportedExprType(scopedHeap, SUPPORTED_EXPONENTIATE_OPERAND_TYPES);

    return Types.FLOAT;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(new StringBuilder("Math.pow("));
    res = res.createMerged(this.getChildren().get(0).generateJavaSourceOutput(scopedHeap));
    res.javaSourceBody().append(", ");
    res = res.createMerged(this.getChildren().get(1).generateJavaSourceOutput(scopedHeap));
    res.javaSourceBody().append(")");
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return Math.pow(
        (double) this.getChildren().get(0).generateInterpretedOutput(scopedHeap),
        (double) this.getChildren().get(1).generateInterpretedOutput(scopedHeap)
    );

  }
}
