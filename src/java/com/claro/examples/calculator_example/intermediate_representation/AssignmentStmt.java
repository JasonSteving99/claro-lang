package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class AssignmentStmt extends Stmt {

  // TODO(steving) This should just be a child IdentifierReferenceTerm passed to the superclass.
  private final String IDENTIFIER;
  // This is only set after the compiler's type-checking phase.
  private Type identifierValidatedType;

  public AssignmentStmt(String identifier, Expr e) {
    super(ImmutableList.of(e));
    this.IDENTIFIER = identifier;
  }

  @Override
  protected void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    Preconditions.checkState(
        scopedHeap.isIdentifierDeclared(this.IDENTIFIER),
        "Attempting to assign to identifier <%s> without declaring it!"
    );
    this.identifierValidatedType = scopedHeap.getValidatedIdentifierType(this.IDENTIFIER);
    ((Expr) this.getChildren().get(0)).assertExpectedExprType(scopedHeap, this.identifierValidatedType);
    scopedHeap.initializeIdentifier(this.IDENTIFIER);
  }

  @Override
  protected GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder res = new StringBuilder();
    scopedHeap.initializeIdentifier(this.IDENTIFIER);
    res.append(
        String.format(
            "%s = %s;\n",
            this.IDENTIFIER,
            ((Expr) this.getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap)
        )
    );
    return GeneratedJavaSource.forJavaSourceBody(res);
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Put the computed value of this identifier directly in the heap.
    scopedHeap.updateIdentifierValue(
        this.IDENTIFIER,
        this.getChildren().get(0).generateInterpretedOutput(scopedHeap)
    );
    scopedHeap.initializeIdentifier(this.IDENTIFIER);
    return null;
  }
}
