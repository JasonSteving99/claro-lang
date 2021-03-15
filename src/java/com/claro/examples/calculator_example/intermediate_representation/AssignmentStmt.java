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
      // TODO(steving) Need to delegate this downstream. Have Exprs impl a method returning their own typing info.
      String type;
      if (this.getChildren().get(0) instanceof ListExpr) {
        // TODO(steving) This ArrayList<Object> will break our type system, need an actual concrete type.
        String typeFormatString = "ClaroList<%s>";
        ListExpr listExpr = (ListExpr) this.getChildren().get(0);
        type = String.format(typeFormatString, listExpr.getJavaSourceType());
      } else {
        type = "double";
      }
      res.append(String.format("%s %s;\n", type, this.IDENTIFIER));
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
