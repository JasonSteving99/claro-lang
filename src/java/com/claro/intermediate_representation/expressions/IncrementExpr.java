package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.function.Supplier;

public class IncrementExpr extends Expr {
  private final boolean preIncrement;

  public IncrementExpr(IdentifierReferenceTerm identifierReferenceTerm, boolean preIncrement, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(identifierReferenceTerm), currentLine, currentLineNumber, startCol, endCol);
    this.preIncrement = preIncrement;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    IdentifierReferenceTerm id = (IdentifierReferenceTerm) this.getChildren().get(0);
    id.assertExpectedExprType(scopedHeap, Types.INTEGER);

    // Additionally, this is a mutating operation, so that means we need to restrict it within Lambda scopes, so if it
    // happens that this is a lambda captured variable, then we should reject this increment.
    Optional<Integer> identifierScopeLevel = scopedHeap.findIdentifierInitializedScopeLevel(id.identifier);
    if (identifierScopeLevel.isPresent()
        && scopedHeap.scopeStack.get(identifierScopeLevel.get())
            .lambdaScopeCapturedVariables.containsKey(id.identifier)) {
      this.logTypeError(ClaroTypeException.forIllegalMutationOfLambdaCapturedVariable());
    }

    return Types.INTEGER;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    StringBuilder res = ((Expr) getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap);
    if (preIncrement) {
      res.insert(0, "++");
    } else {
      res.append("++");
    }
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    IdentifierReferenceTerm identifierReferenceTerm = (IdentifierReferenceTerm) getChildren().get(0);
    Integer res = (Integer) identifierReferenceTerm.generateInterpretedOutput(scopedHeap);
    scopedHeap.updateIdentifierValue(identifierReferenceTerm.getIdentifier(), res + 1);
    if (preIncrement) {
      res = res + 1;
    }
    return res;
  }
}
