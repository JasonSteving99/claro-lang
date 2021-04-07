package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.CalculatorParserException;
import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class SubtractNumericExpr extends NumericExpr {

  private static final ImmutableSet<Type> SUPPORTED_SUBTRACT_OPERAND_TYPES =
      ImmutableSet.of(Types.INTEGER, Types.DOUBLE);
  private static final String TYPE_SUPPORT_ERROR_MESSAGE =
      "Internal Compiler Error: Currently `-` is not supported for types other than Integer and Double.";

  // TODO(steving) This should only accept other NumericExpr args. Need to update the grammar.
  public SubtractNumericExpr(Expr lhs, Expr rhs) {
    super(ImmutableList.of(lhs, rhs));
  }

  @Override
  protected Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Type lhs = ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap);
    Type rhs = ((Expr) this.getChildren().get(1)).getValidatedExprType(scopedHeap);

    assertSupportedExprType(lhs, SUPPORTED_SUBTRACT_OPERAND_TYPES);
    assertSupportedExprType(rhs, SUPPORTED_SUBTRACT_OPERAND_TYPES);

    if (lhs.equals(Types.DOUBLE) || rhs.equals(Types.DOUBLE)) {
      return Types.DOUBLE;
    } else {
      return Types.INTEGER;
    }
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            "(%s - %s)",
            this.getChildren().get(0).generateJavaSourceOutput(scopedHeap),
            this.getChildren().get(1).generateJavaSourceOutput(scopedHeap)
        )
    );
  }

  // TODO(steving) This might be the point where switching the compiler implementation to ~Kotlin~ will be a legitimate
  // TODO(steving) win. I believe that Kotlin supports multiple-dispatch which I think would allow this entire garbage
  // TODO(steving) mess of instanceof checks to be reduced to a single function call passing the lhs and rhs, and that
  // TODO(steving) function would have a few different impls taking args of different types and the correct one would be
  // TODO(steving) called. I guess in that case it's just the runtime itself handling these instanceof checks.
  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    Object lhs = this.getChildren().get(0).generateInterpretedOutput(scopedHeap);
    Object rhs = this.getChildren().get(1).generateInterpretedOutput(scopedHeap);
    if (lhs instanceof Double && rhs instanceof Double) {
      return (Double) lhs - (Double) rhs;
    } else if (lhs instanceof Integer && rhs instanceof Integer) {
      return (Integer) lhs - (Integer) rhs;
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
      return lhsDouble - rhsDouble;
    } else {
      // TODO(steving) In the future, assume that operator- is able to be used on arbitrary Comparable impls. So check
      // TODO(steving) the type of each in the heap and see if they are implementing Comparable, and call their impl of
      // TODO(steving) Operators::minus.
      throw new CalculatorParserException(TYPE_SUPPORT_ERROR_MESSAGE);
    }
  }
}
