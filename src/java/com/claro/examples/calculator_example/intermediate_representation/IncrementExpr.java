package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

public class IncrementExpr extends Expr {
  private final boolean preIncrement;

  public IncrementExpr(IdentifierReferenceTerm identifierReferenceTerm, boolean preIncrement) {
    super(ImmutableList.of(identifierReferenceTerm));
    this.preIncrement = preIncrement;
  }

  @Override
  protected Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    ((Expr) this.getChildren().get(0)).assertExpectedExprType(scopedHeap, Types.INTEGER);

    return Types.INTEGER;
  }

  @Override
  protected StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    StringBuilder res = ((Expr) getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap);
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
