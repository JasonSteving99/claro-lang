package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

public class IncrementExpr extends Expr {
  private final boolean preIncrement;

  public IncrementExpr(IdentifierReferenceTerm identifierReferenceTerm, boolean preIncrement) {
    super(ImmutableList.of(identifierReferenceTerm));
    // TODO(steving) Assert that the IdentifierReferenceTerm is of type Integer.
    this.preIncrement = preIncrement;
  }

  @Override
  protected Type getValidatedExprType(ScopedHeap scopedHeap) {
    return Types.INTEGER;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder res = getChildren().get(0).generateJavaSourceOutput(scopedHeap);
    if (preIncrement) {
      res.insert(0, "++");
    } else {
      res.append("++");
    }
    return res;
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    IdentifierReferenceTerm identifierReferenceTerm = (IdentifierReferenceTerm) getChildren().get(0);
    Integer res = (Integer) identifierReferenceTerm.generateInterpretedOutput(scopedHeap);
    scopedHeap.updateIdentifierValue(identifierReferenceTerm.getIdentifier(), res + 1);
    if (preIncrement) {
      res = res + 1;
    }
    return res;
  }
}
