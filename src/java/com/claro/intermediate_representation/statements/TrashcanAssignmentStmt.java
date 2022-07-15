package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.google.common.collect.ImmutableList;

public class TrashcanAssignmentStmt extends Stmt {
  private final IdentifierReferenceTerm identifier;

  public TrashcanAssignmentStmt(IdentifierReferenceTerm identifier) {
    super(ImmutableList.of());
    this.identifier = identifier;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // This is the trashcan... you can throw literally anything into the trash if you for some
    // reason feel like that's a good idea. Just mark the identifier used.
    identifier.getValidatedExprType(scopedHeap);
    // TODO(steving) Need to do this right. Need to actually make sure that you can't rereference this variable after.
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    scopedHeap.markIdentifierUsed(this.identifier.getIdentifier());
    return GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    scopedHeap.markIdentifierUsed(this.identifier.getIdentifier());
    return null;
  }
}
