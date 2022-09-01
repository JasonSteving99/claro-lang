package com.claro.intermediate_representation.expressions.numeric;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.function.Supplier;

public class MultiplyNumericExpr extends NumericExpr {
  // TODO(steving) In the future, assume that operator* is able to be used on arbitrary Comparable impls. So check
  // TODO(steving) the type of each in the heap and see if they are implementing Comparable, and call their impl of
  // TODO(steving) Operators::multiply.
  private static final ImmutableSet<Type> SUPPORTED_MULTIPLY_OPERAND_TYPES =
      ImmutableSet.of(Types.INTEGER, Types.FLOAT);
  private static final String TYPE_SUPPORT_ERROR_MESSAGE =
      "Internal Compiler Error: Currently `*` is not supported for types other than Integer and Double.";

  // TODO(steving) This should only accept other NumericExpr args. Need to update the grammar.
  public MultiplyNumericExpr(Expr lhs, Expr rhs, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(lhs, rhs), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Expr lhs = (Expr) this.getChildren().get(0);
    Expr rhs = (Expr) this.getChildren().get(1);

    Type actualLhsType = lhs.assertSupportedExprType(scopedHeap, SUPPORTED_MULTIPLY_OPERAND_TYPES);
    Type actualRhsType = rhs.assertSupportedExprType(scopedHeap, SUPPORTED_MULTIPLY_OPERAND_TYPES);

    if (actualLhsType.equals(Types.FLOAT) || actualRhsType.equals(Types.FLOAT)) {
      return Types.FLOAT;
    } else {
      return Types.INTEGER;
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource exprGenJavaSource0 = this.getChildren().get(0).generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource exprGenJavaSource1 = this.getChildren().get(1).generateJavaSourceOutput(scopedHeap);

    GeneratedJavaSource mulExprGenJavaSource =
        GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder(
                String.format(
                    "(%s * %s)",
                    exprGenJavaSource0.javaSourceBody().toString(),
                    exprGenJavaSource1.javaSourceBody().toString()
                )));

    // We've already used the javaSourceBody's, we're safe to clear them.
    exprGenJavaSource0.javaSourceBody().setLength(0);
    exprGenJavaSource1.javaSourceBody().setLength(0);
    return mulExprGenJavaSource.createMerged(exprGenJavaSource0).createMerged(exprGenJavaSource1);
  }

  // TODO(steving) This might be the point where switching the compiler implementation to ~Kotlin~ will be a legitimate
  // TODO(steving) win. I believe that Kotlin supports multiple-dispatch which I think would allow this entire garbage
  // TODO(steving) mess of instanceof checks to be reduced to a single function call passing the lhs and rhs, and that
  // TODO(steving) function would have a few different impls taking args of different types and the correct one would be
  // TODO(steving) called. I guess in that case it's just the runtime itself handling these instanceof checks.
  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    Object lhs = this.getChildren().get(0).generateInterpretedOutput(scopedHeap);
    Object rhs = this.getChildren().get(1).generateInterpretedOutput(scopedHeap);
    if (lhs instanceof Double && rhs instanceof Double) {
      return (Double) lhs * (Double) rhs;
    } else if (lhs instanceof Integer && rhs instanceof Integer) {
      return (Integer) lhs * (Integer) rhs;
    } else if ((lhs instanceof Integer && rhs instanceof Double) || (lhs instanceof Double && rhs instanceof Integer)) {
      Double lhsDouble;
      Double rhsDouble;
      if (lhs instanceof Integer) {
        lhsDouble = ((Integer) lhs).doubleValue();
        rhsDouble = (Double) rhs;
      } else {
        lhsDouble = (Double) lhs;
        rhsDouble = ((Integer) rhs).doubleValue();
      }
      return lhsDouble * rhsDouble;
    } else {
      throw new ClaroParserException(TYPE_SUPPORT_ERROR_MESSAGE);
    }
  }
}
