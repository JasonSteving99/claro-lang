package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public abstract class BoolExpr extends Expr {
  // For now this class is left empty, though in the future it'll likely contain at least typing information.
  public BoolExpr(ImmutableList<Node> children) {
    super(children);
  }

  @Override
  protected Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    final ImmutableSet<Type> supportedOperandTypes = getSupportedOperandTypes();

    // BoolExprs support either 1 or 2 operands.
    if (this.getChildren().size() == 1) {
      if (supportedOperandTypes.size() > 0) {
        assertSupportedExprType(
            ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap), supportedOperandTypes);
      }
    } else {
      Type lhsActualType = ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap);
      if (supportedOperandTypes.isEmpty()) {
        ((Expr) this.getChildren().get(1)).assertExpectedExprType(scopedHeap, lhsActualType);
      } else {
        assertSupportedExprType(lhsActualType, supportedOperandTypes);
        assertSupportedExprType(
            ((Expr) this.getChildren().get(1)).getValidatedExprType(scopedHeap), supportedOperandTypes);
      }
    }

    return Types.BOOLEAN;
  }

  // Return empty if you want to allow any type, with the condition that both operands are the same type. Otherwise
  // operands' types may differ as long as they're in the supported set.
  protected abstract ImmutableSet<Type> getSupportedOperandTypes();
}
