package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.runtime_utilities.ClaroRuntimeUtilities;
import com.google.common.collect.ImmutableList;

public class CastExpr extends Expr {
  private final Type assertedType;
  private final Expr castedExpr;

  public CastExpr(Type assertedType, Expr castedExpr) {
    super(ImmutableList.of(castedExpr));
    this.assertedType = assertedType;
    this.castedExpr = castedExpr;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // For now, during the type validation phase, The only thing that the CastExpr must assert is that the casted expr
    // in fact currently has an UNDECIDED type.
    // TODO(steving) In the long term, we may need to extend this logic to also handle the case where we're casting
    //  because of some co/contra-variance instead of a compile-time-undecidable type situation.
    this.castedExpr.setAcceptUndecided(true);
    this.castedExpr.assertExpectedBaseType(scopedHeap, BaseType.UNDECIDED);

    // We're trusting the programmer by contract at compile-time. At runtime we'll generate code to check if they were
    // actually right.
    return this.assertedType;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    return new StringBuilder(
        String.format(
            "ClaroRuntimeUtilities.assertedTypeValue(%s, %s)",
            this.assertedType.getJavaSourceClaroType(),
            this.castedExpr.generateJavaSourceBodyOutput(scopedHeap)
        )
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    Object evaluatedCastedExprValue = this.castedExpr.generateInterpretedOutput(scopedHeap);
    return ClaroRuntimeUtilities.assertedTypeValue(assertedType, evaluatedCastedExprValue);
  }
}