package com.claro.intermediate_representation.expressions.bool;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.function.Supplier;

// TODO(steving) Btw, this class itself is an example where I want structural templating of some sort. This class's
// TODO(steving) structure is literally perfect for all binary operators, but for some reason I can't reuse the
// TODO(steving) file to express all of them, I have to copy-paste this file and swap the specific operator call.
// TODO(steving) Hopefully, Claro can do something about this.
public class LessThanBoolExpr extends BoolExpr {

  public LessThanBoolExpr(Expr lhs, Expr rhs, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(lhs, rhs), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  protected ImmutableSet<Type> getSupportedOperandTypes() {
    return ImmutableSet.of(Types.INTEGER, Types.LONG, Types.FLOAT, Types.DOUBLE);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource exprGenJavaSource0 = this.getChildren().get(0).generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource exprGenJavaSource1 = this.getChildren().get(1).generateJavaSourceOutput(scopedHeap);

    GeneratedJavaSource addExprGenJavaSource =
        GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder(
                String.format(
                    "(%s < %s)",
                    exprGenJavaSource0.javaSourceBody().toString(),
                    exprGenJavaSource1.javaSourceBody().toString()
                )));

    // We've already used the javaSourceBody's, we're safe to clear them.
    exprGenJavaSource0.javaSourceBody().setLength(0);
    exprGenJavaSource1.javaSourceBody().setLength(0);
    return addExprGenJavaSource.createMerged(exprGenJavaSource0).createMerged(exprGenJavaSource1);
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
      return (Double) lhs < (Double) rhs;
    } else if (lhs instanceof Integer && rhs instanceof Integer) {
      return (Integer) lhs < (Integer) rhs;
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
      return lhsDouble < rhsDouble;
    } else {
      // TODO(steving) In the future, assume that operator< is able to be used on arbitrary Comparable impls. So check
      // TODO(steving) the type of each in the heap and see if they are implementing Comparable, and call their impl of
      // TODO(steving) Comparable::lessThan.
      throw new ClaroParserException(
          "Internal Compiler Error: Currently `<` is not supported for types other than Integer and Double.");
    }
  }
}
