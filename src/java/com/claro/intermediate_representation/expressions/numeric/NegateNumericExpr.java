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

public class NegateNumericExpr extends NumericExpr {

  // TODO(steving) This should only accept other NumericExpr args. Need to update the grammar.
  public NegateNumericExpr(Expr e, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(e), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    return ((Expr) this.getChildren().get(0))
        .assertSupportedExprType(scopedHeap, ImmutableSet.of(Types.INTEGER, Types.FLOAT));
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            "(-%s)",
            ((Expr) this.getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap)
        )
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    Object value = this.getChildren().get(0).generateInterpretedOutput(scopedHeap);
    if (value instanceof Double) {
      return -((double) this.getChildren().get(0).generateInterpretedOutput(scopedHeap));
    } else if (value instanceof Integer) {
      return -((int) this.getChildren().get(0).generateInterpretedOutput(scopedHeap));
    } else {
      // TODO(steving) In the future, assume that operator-negate is able to be used on arbitrary Comparable impls. So
      // TODO(steving) check the type of each in the heap and see if they are implementing Comparable, and call their
      // TODO(steving) impl of Operators::negate.
      throw new ClaroParserException(
          "Internal Compiler Error: Currently `-`(negate) is not supported for types other than Integer and Double.");
    }
  }
}
