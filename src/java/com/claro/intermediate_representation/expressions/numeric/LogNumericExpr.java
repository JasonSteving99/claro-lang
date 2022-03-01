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

public class LogNumericExpr extends NumericExpr {

  private static final ImmutableSet<Type> SUPPORTED_LOG_ARG_TYPES = ImmutableSet.of(Types.INTEGER, Types.FLOAT);

  // TODO(steving) This should only accept other NumericExpr args. Need to update the grammar.
  public LogNumericExpr(Expr arg, Expr log_base, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(arg, log_base), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    ((Expr) this.getChildren().get(0)).assertSupportedExprType(scopedHeap, SUPPORTED_LOG_ARG_TYPES);
    ((Expr) this.getChildren().get(1)).assertSupportedExprType(scopedHeap, SUPPORTED_LOG_ARG_TYPES);

    return Types.FLOAT;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            "(Math.log(%s) / Math.log(%s))",
            ((Expr) this.getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap),
            ((Expr) this.getChildren().get(1)).generateJavaSourceBodyOutput(scopedHeap)
        )
    );
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
      return Math.log((Double) lhs) / Math.log((Double) rhs);
    } else if (lhs instanceof Integer && rhs instanceof Integer) {
      return Math.log((Integer) lhs) / Math.log((Integer) rhs);
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
      return Math.log(lhsDouble) / Math.log(rhsDouble);
    } else {
      // TODO(steving) In the future, assume that operator-log is able to be used on arbitrary Comparable impls. So check
      // TODO(steving) the type of each in the heap and see if they are implementing Comparable, and call their impl of
      // TODO(steving) Operators::log.
      throw new ClaroParserException(
          "Internal Compiler Error: Currently `log_*` is not supported for types other than Integer and Double.");
    }
  }
}
