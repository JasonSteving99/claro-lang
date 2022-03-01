package com.claro.intermediate_representation.expressions.bool;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.Node;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.function.Supplier;

public abstract class BoolExpr extends Expr {
  // For now this class is left empty, though in the future it'll likely contain at least typing information.
  public BoolExpr(ImmutableList<Node> children, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(children, currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    final ImmutableSet<Type> supportedOperandTypes = getSupportedOperandTypes();

    // BoolExprs support either 1 or 2 operands.
    if (this.getChildren().size() == 1) {
      if (supportedOperandTypes.size() > 0) {
        ((Expr) this.getChildren().get(0)).assertSupportedExprType(scopedHeap, supportedOperandTypes);
      }
    } else {
      Expr lhs = (Expr) this.getChildren().get(0);
      Expr rhs = (Expr) this.getChildren().get(1);
      if (supportedOperandTypes.isEmpty()) {
        rhs.assertExpectedExprType(scopedHeap, lhs.getValidatedExprType(scopedHeap));
      } else {
        lhs.assertSupportedExprType(scopedHeap, supportedOperandTypes);
        rhs.assertSupportedExprType(scopedHeap, supportedOperandTypes);
      }
    }

    return Types.BOOLEAN;
  }

  // Return empty if you want to allow any type, with the condition that both operands are the same type. Otherwise
  // operands' types may differ as long as they're in the supported set.
  protected abstract ImmutableSet<Type> getSupportedOperandTypes();
}
