package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.google.common.collect.ImmutableList;

import java.util.Optional;

public class TrashcanAssignmentStmt extends Stmt {
  private final Expr expr;
  private final Optional<AutomaticErrorPropagationStmt> optionalAutomaticErrorPropagationStmt;

  public TrashcanAssignmentStmt(Expr expr) {
    super(ImmutableList.of());
    this.expr = expr;
    this.optionalAutomaticErrorPropagationStmt = Optional.empty();
  }

  public TrashcanAssignmentStmt(Expr expr, boolean errorProp) {
    super(ImmutableList.of());
    this.expr = expr;
    this.optionalAutomaticErrorPropagationStmt =
        Optional.of(new AutomaticErrorPropagationStmt(Optional.empty(), this.expr));
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (!this.optionalAutomaticErrorPropagationStmt.isPresent()) {
      // This is the basic trashcan... you can throw literally anything into the trash if you for some
      // reason feel like that's a good idea. Just mark the identifier used.
      this.expr.getValidatedExprType(scopedHeap);
    } else {
      // However, if you're attempting to do automatic error propagation, then in fact we need additional validation.
      this.optionalAutomaticErrorPropagationStmt.get().getValidatedExprType(scopedHeap);
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    if (!this.optionalAutomaticErrorPropagationStmt.isPresent()) {
      if (this.expr instanceof IdentifierReferenceTerm) {
        scopedHeap.markIdentifierUsed(((IdentifierReferenceTerm) this.expr).getIdentifier());
        return GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
      } else { // Here we've got some expr whose side effect the user wants to keep, but ignore the result value.
        GeneratedJavaSource res = this.expr.generateJavaSourceOutput(scopedHeap);
        res.javaSourceBody().append(';');
        return res;
      }
    } else {
      // Here we're going to do something interesting. The AutomaticErrorPropagationStmt is going to be adding a prefix
      // stmt which includes the conditional checking and then return a GeneratedJavaSource with a javaSourceBody that
      // just references back to the synthetic variable generated for the prefix stmt. But since the trashcan assignment
      // means that we're throwing away the result, there's actually no use for the GeneratedJavaSource returned by the
      // AutomaticErrorPropagationStmt. We'll just return an empty one instead.
      this.optionalAutomaticErrorPropagationStmt.get().generateJavaSourceOutput(scopedHeap);
      return GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
    }
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    if (!this.optionalAutomaticErrorPropagationStmt.isPresent()) {
      if (this.expr instanceof IdentifierReferenceTerm) {
        scopedHeap.markIdentifierUsed(((IdentifierReferenceTerm) this.expr).getIdentifier());
      } else { // Here we've got a provider/function call whose side effect we want to keep, but ignore the result value.
        this.expr.generateInterpretedOutput(scopedHeap);
      }
      return null;
    } else {
      // Maybe returning some non-null thing to signal early return. See comment in ReturnStmt::generateInterpretedOutput
      // for an explanation.
      return this.optionalAutomaticErrorPropagationStmt.get().generateInterpretedOutput(scopedHeap);
    }
  }
}
