package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.google.common.collect.ImmutableList;

public class TrashcanAssignmentStmt extends Stmt {
  private final Expr expr;

  public TrashcanAssignmentStmt(Expr expr) {
    super(ImmutableList.of());
    this.expr = expr;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // This is the trashcan... you can throw literally anything into the trash if you for some
    // reason feel like that's a good idea. Just mark the identifier used.
    this.expr.getValidatedExprType(scopedHeap);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    if (this.expr instanceof IdentifierReferenceTerm) {
      scopedHeap.markIdentifierUsed(((IdentifierReferenceTerm) this.expr).getIdentifier());
      return GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
    } else { // Here we've got a provider/function call whose side effect we want to keep, but ignore the result value.
      GeneratedJavaSource res = this.expr.generateJavaSourceOutput(scopedHeap);
      res.javaSourceBody().append(';');
      return res;
    }
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    if (this.expr instanceof IdentifierReferenceTerm) {
      scopedHeap.markIdentifierUsed(((IdentifierReferenceTerm) this.expr).getIdentifier());
    } else { // Here we've got a provider/function call whose side effect we want to keep, but ignore the result value.
      this.expr.generateInterpretedOutput(scopedHeap);
    }
    return null;
  }
}
