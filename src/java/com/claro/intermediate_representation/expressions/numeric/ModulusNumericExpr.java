package com.claro.intermediate_representation.expressions.numeric;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.function.Supplier;

public class ModulusNumericExpr extends NumericExpr {

  private static final ImmutableSet<Type> SUPPORTED_DIVIDE_OPERAND_TYPES =
      ImmutableSet.of(Types.INTEGER, Types.LONG, Types.FLOAT, Types.DOUBLE);
  private final String TYPE_SUPPORT_ERROR_MESSAGE =
      String.format(
          "Internal Compiler Error: Currently `*` is not supported for types other than %s.",
          SUPPORTED_DIVIDE_OPERAND_TYPES
      );

  private Type maybePromotedResultType = null;


  // TODO(steving) This should only accept other NumericExpr args. Need to update the grammar.
  public ModulusNumericExpr(Expr lhs, Expr rhs, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(lhs, rhs), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Expr lhs = (Expr) this.getChildren().get(0);
    Expr rhs = (Expr) this.getChildren().get(1);

    Type actualLhsType = lhs.assertSupportedExprType(scopedHeap, SUPPORTED_DIVIDE_OPERAND_TYPES);
    Type actualRhsType = rhs.assertSupportedExprType(scopedHeap, SUPPORTED_DIVIDE_OPERAND_TYPES);

    // Handle auto-promotion when operands are potentially different types.
    if (actualLhsType.equals(actualRhsType)) {
      this.maybePromotedResultType = actualLhsType;
    } else if (actualLhsType.equals(Types.DOUBLE) || actualRhsType.equals(Types.DOUBLE)) {
      this.maybePromotedResultType = Types.DOUBLE;
    } else if (actualLhsType.equals(Types.FLOAT) || actualRhsType.equals(Types.FLOAT)) {
      this.maybePromotedResultType = Types.FLOAT;
    } else if (actualLhsType.equals(Types.LONG) || actualRhsType.equals(Types.LONG)) {
      this.maybePromotedResultType = Types.LONG;
    } else {
      this.maybePromotedResultType = Types.INTEGER;
    }
    return this.maybePromotedResultType;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource exprGenJavaSource0 = this.getChildren().get(0).generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource exprGenJavaSource1 = this.getChildren().get(1).generateJavaSourceOutput(scopedHeap);

    GeneratedJavaSource divExprGenJavaSource =
        GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder(
                String.format(
                    "%s.valueOf(%s %% %s)",
                    this.maybePromotedResultType.getJavaSourceType(),
                    exprGenJavaSource0.javaSourceBody().toString(),
                    exprGenJavaSource1.javaSourceBody().toString()
                )));

    // We've already used the javaSourceBody's, we're safe to clear them.
    exprGenJavaSource0.javaSourceBody().setLength(0);
    exprGenJavaSource1.javaSourceBody().setLength(0);
    return divExprGenJavaSource.createMerged(exprGenJavaSource0).createMerged(exprGenJavaSource1);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    Object lhs = this.getChildren().get(0).generateInterpretedOutput(scopedHeap);
    Object rhs = this.getChildren().get(1).generateInterpretedOutput(scopedHeap);
    if (lhs instanceof Double && rhs instanceof Double) {
      return (Double) lhs % (Double) rhs;
    } else if (lhs instanceof Integer && rhs instanceof Integer) {
      return (Integer) lhs % (Integer) rhs;
    } else { // Then: ((lhs instanceof Integer && rhs instanceof Double) || (lhs instanceof Double && rhs instanceof Integer)) {
      Double lhsDouble;
      Double rhsDouble;
      if (lhs instanceof Integer) {
        lhsDouble = ((Integer) lhs).doubleValue();
        rhsDouble = (Double) rhs;
      } else {
        lhsDouble = (Double) lhs;
        rhsDouble = ((Integer) rhs).doubleValue();
      }
      return lhsDouble % rhsDouble;
    }
  }
}
