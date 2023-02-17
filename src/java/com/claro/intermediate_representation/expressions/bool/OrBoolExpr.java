package com.claro.intermediate_representation.expressions.bool;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class OrBoolExpr extends BoolExpr {

  public OrBoolExpr(Expr lhs, Expr rhs, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(lhs, rhs), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  protected ImmutableSet<Type> getSupportedOperandTypes() {
    return ImmutableSet.of(Types.BOOLEAN);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Type validatedType = super.getValidatedExprType(scopedHeap);

    // Detect any type narrowing information that's learned from *both* conditional branches.
    for (Map.Entry<String, Type> lhsTypeNarrowingEntry : getOneofsToBeNarrowed((Expr) this.getChildren()
        .get(0)).entrySet()) {
      HashMap<String, Type> rhsOneofsToBeNarrowed = getOneofsToBeNarrowed((Expr) this.getChildren().get(1));
      if (rhsOneofsToBeNarrowed.containsKey(lhsTypeNarrowingEntry.getKey())
          && rhsOneofsToBeNarrowed.get(lhsTypeNarrowingEntry.getKey()).equals(lhsTypeNarrowingEntry.getValue())) {
        this.oneofsToBeNarrowed.put(lhsTypeNarrowingEntry.getKey(), lhsTypeNarrowingEntry.getValue());
      }
    }

    return validatedType;
  }

  private static HashMap<String, Type> getOneofsToBeNarrowed(Expr operand) {
    if (operand.getClass().getSimpleName().equals("ParenthesizedExpr")) {
      return ((BoolExpr) operand.getChildren().get(0)).oneofsToBeNarrowed;
    } else if (operand instanceof BoolExpr) {
      return ((BoolExpr) operand).oneofsToBeNarrowed;
    } else {
      return Maps.newHashMap();
    }
  }

  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource lhsGeneratedJavaSource = this.getChildren().get(0).generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource rhsGeneratedJavaSource = this.getChildren().get(1).generateJavaSourceOutput(scopedHeap);

    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                "(%s || %s)",
                lhsGeneratedJavaSource.javaSourceBody(),
                rhsGeneratedJavaSource.javaSourceBody()
            )
        ));

    // Need to ensure that we pick up the static definitions from each child expr.
    res = res.createMerged(
        GeneratedJavaSource.create(
            // We've already picked up the javaSourceBody, so we don't need it again.
            new StringBuilder(),
            lhsGeneratedJavaSource.optionalStaticDefinitions(),
            lhsGeneratedJavaSource.optionalStaticPreambleStmts()
        )).createMerged(
        GeneratedJavaSource.create(
            // We've already picked up the javaSourceBody, so we don't need it again.
            new StringBuilder(),
            rhsGeneratedJavaSource.optionalStaticDefinitions(),
            rhsGeneratedJavaSource.optionalStaticPreambleStmts()
        ));

    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return
        (boolean) this.getChildren().get(0).generateInterpretedOutput(scopedHeap) ||
        (boolean) this.getChildren().get(1).generateInterpretedOutput(scopedHeap);
  }
}
