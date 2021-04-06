package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

public class DecrementExpr extends Expr {
  private final boolean preDecrement;

  public DecrementExpr(IdentifierReferenceTerm identifierReferenceTerm, boolean preDecrement) {
    super(ImmutableList.of(identifierReferenceTerm));
    // TODO(steving) Assert that the IdentifierReferenceTerm is of type Integer.
    this.preDecrement = preDecrement;
  }

  @Override
  protected Type getValidatedExprType(ScopedHeap scopedHeap) {
    return Types.INTEGER;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder res = getChildren().get(0).generateJavaSourceOutput(scopedHeap);
    if (preDecrement) {
      res.insert(0, "--");
    } else {
      res.append("--");
    }
    return res;
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    IdentifierReferenceTerm identifierReferenceTerm = (IdentifierReferenceTerm) getChildren().get(0);
    Integer res = (Integer) identifierReferenceTerm.generateInterpretedOutput(scopedHeap);
    scopedHeap.updateIdentifierValue(identifierReferenceTerm.getIdentifier(), res - 1);
    if (preDecrement) {
      res = res - 1;
    }
    return res;
  }
}
