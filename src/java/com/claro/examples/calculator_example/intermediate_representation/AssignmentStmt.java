package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.google.common.collect.ImmutableList;

public class AssignmentStmt extends Stmt {

  // TODO(steving) This should just be a child IdentifierReferenceTerm passed to the superclass.
  private final String IDENTIFIER;

  public AssignmentStmt(String identifier, Expr e) {
    super(ImmutableList.of(e));
    this.IDENTIFIER = identifier;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder res = new StringBuilder();
    if (!scopedHeap.isIdentifierDeclared(this.IDENTIFIER)) {
      // First time we're seeing the variable, so declare it.
      res.append(String.format("double %s;\n", this.IDENTIFIER));
    }
    scopedHeap.putIdentifierValue(this.IDENTIFIER);
    res.append(String.format("%s = %s;\n", this.IDENTIFIER, this.getChildren()
        .get(0)
        .generateJavaSourceOutput(scopedHeap)
        .toString()));
    return res;
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Put the computed value of this identifier directly in the heap.
    scopedHeap.putIdentifierValue(this.IDENTIFIER, this.getChildren().get(0).generateInterpretedOutput(scopedHeap));
    return null;
  }
}
