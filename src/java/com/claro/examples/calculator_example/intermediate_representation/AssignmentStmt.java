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
    Expr assignedValueExpr = (Expr) this.getChildren().get(0);
    StringBuilder res = new StringBuilder();
    // TODO(steving) It actually gets confusing to not require the user to explicitly declare the new variable. Instead,
    // TODO(steving) let's reintroduce explicit declarations using the `var` keyword for type inference on assignment.
    // TODO(steving) And we'll use `var <varname>: <type> [= <Expr>;]` to declare a var w/o initializing or to declare
    // TODO(steving) super explicitly during init, or to init when type inference isn't possible/supported.
    if (!scopedHeap.isIdentifierDeclared(this.IDENTIFIER)) {
      // First time we're seeing the variable, so declare it.
      // TODO(steving) Need to delegate this downstream. Have Exprs impl a method returning their own typing info.
      String type;
      if (assignedValueExpr instanceof ListExpr) {
        // TODO(steving) This ArrayList<Object> will break our type system, need an actual concrete type.
        String typeFormatString = "ClaroList<%s>";
        ListExpr listExpr = (ListExpr) assignedValueExpr;
        type = String.format(typeFormatString, listExpr.getJavaSourceType());
      } else {
        type = "double";
      }
      res.append(String.format("%s %s;\n", type, this.IDENTIFIER));
    }
    scopedHeap.putIdentifierValue(this.IDENTIFIER, assignedValueExpr.getValidatedExprType());
    res.append(
        String.format(
            "%s = %s;\n",
            this.IDENTIFIER,
            assignedValueExpr.generateJavaSourceOutput(scopedHeap).toString()
        )
    );
    return res;
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    Expr assignedValueExpr = (Expr) this.getChildren().get(0);
    // Put the computed value of this identifier directly in the heap.
    scopedHeap.putIdentifierValue(
        this.IDENTIFIER,
        assignedValueExpr.getValidatedExprType(),
        assignedValueExpr.generateInterpretedOutput(scopedHeap)
    );
    return null;
  }
}
