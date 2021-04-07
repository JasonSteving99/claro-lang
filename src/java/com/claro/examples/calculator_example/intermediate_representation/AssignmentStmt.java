package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;

import java.util.Optional;

public class AssignmentStmt extends Stmt {

  // TODO(steving) This should just be a child IdentifierReferenceTerm passed to the superclass.
  private final String IDENTIFIER;

  private final Optional<Type> optionalIdentifierDeclaredType;
  private Type identifierValidatedInferredType;

  public AssignmentStmt(String identifier, Expr e) {
    super(ImmutableList.of(e));
    this.IDENTIFIER = identifier;
    this.optionalIdentifierDeclaredType = Optional.empty();
  }

  public AssignmentStmt(String identifier, Type declaredType, Expr e) {
    super(ImmutableList.of(e));
    this.IDENTIFIER = identifier;
    this.optionalIdentifierDeclaredType = Optional.of(declaredType);
  }

  @Override
  protected void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    Expr assignedValueExpr = (Expr) this.getChildren().get(0);
    if (optionalIdentifierDeclaredType.isPresent()) {
      assignedValueExpr.assertExpectedExprType(scopedHeap, optionalIdentifierDeclaredType.get());
    } else {
      identifierValidatedInferredType = assignedValueExpr.getValidatedExprType(scopedHeap);
    }
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    Type identifierValidatedType = optionalIdentifierDeclaredType.orElse(identifierValidatedInferredType);
    StringBuilder res = new StringBuilder();
    // TODO(steving) It actually gets confusing to not require the user to explicitly declare the new variable. Instead,
    // TODO(steving) let's reintroduce explicit declarations using the `var` keyword for type inference on assignment.
    // TODO(steving) And we'll use `var <varname>: <type> [= <Expr>;]` to declare a var w/o initializing or to declare
    // TODO(steving) super explicitly during init, or to init when type inference isn't possible/supported.
    if (!scopedHeap.isIdentifierDeclared(this.IDENTIFIER)) {
      // First time we're seeing the variable, so declare it.
      res.append(String.format("%s ", identifierValidatedType.getJavaSourceType()));
    }
    scopedHeap.putIdentifierValue(this.IDENTIFIER, identifierValidatedType);
    res.append(
        String.format(
            "%s = %s;\n",
            this.IDENTIFIER,
            this.getChildren().get(0).generateJavaSourceOutput(scopedHeap).toString()
        )
    );
    return res;
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Put the computed value of this identifier directly in the heap.
    scopedHeap.putIdentifierValue(
        this.IDENTIFIER,
        this.optionalIdentifierDeclaredType.orElse(identifierValidatedInferredType),
        this.getChildren().get(0).generateInterpretedOutput(scopedHeap)
    );
    return null;
  }
}
