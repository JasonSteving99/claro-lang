package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.Optional;

public class DeclarationStmt extends Stmt {

  private final String IDENTIFIER;

  // Only oneof these should be set.
  private final Optional<Type> optionalIdentifierDeclaredType;
  private Type identifierValidatedInferredType;

  // Constructor for var initialization requesting type inference.
  public DeclarationStmt(String identifier, Expr e) {
    super(ImmutableList.of(e));
    this.IDENTIFIER = identifier;
    this.optionalIdentifierDeclaredType = Optional.empty();
  }

  // Allow typed declarations with initialization.
  public DeclarationStmt(String identifier, Type declaredType, Expr e) {
    super(ImmutableList.of(e));
    this.IDENTIFIER = identifier;
    this.optionalIdentifierDeclaredType = Optional.of(declaredType);
  }

  // Allow typed declarations without initialization.
  public DeclarationStmt(String identifier, Type declaredType) {
    super(ImmutableList.of());
    this.IDENTIFIER = identifier;
    this.optionalIdentifierDeclaredType = Optional.of(declaredType);
  }

  @Override
  protected void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Validate that this is not a redeclaration of an identifier.
    Preconditions.checkState(
        !scopedHeap.isIdentifierDeclared(this.IDENTIFIER),
        String.format("Unexpected redeclaration of identifier <%s>.", this.IDENTIFIER)
    );

    // Determine which type this identifier was declared as, validating initializer Expr as necessary.
    if (optionalIdentifierDeclaredType.isPresent()) {
      if (!this.getChildren().isEmpty()) {
        ((Expr) this.getChildren().get(0)).assertExpectedExprType(scopedHeap, optionalIdentifierDeclaredType.get());
        scopedHeap.initializeIdentifier(this.IDENTIFIER);
      }
      scopedHeap.observeIdentifier(this.IDENTIFIER, optionalIdentifierDeclaredType.get());
    } else {
      // Infer the identifier's type only the first time it's assigned to.
      this.identifierValidatedInferredType = ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap);
      scopedHeap.observeIdentifier(this.IDENTIFIER, identifierValidatedInferredType);
      scopedHeap.initializeIdentifier(this.IDENTIFIER);
    }
  }

  @Override
  protected GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder res = new StringBuilder();

    Type identifierValidatedType = optionalIdentifierDeclaredType.orElse(identifierValidatedInferredType);

    // First time we're seeing the variable, so declare it.
    res.append(String.format("%s %s", identifierValidatedType.getJavaSourceType(), this.IDENTIFIER));
    scopedHeap.putIdentifierValue(this.IDENTIFIER, identifierValidatedType);

    // Maybe mark the identifier initialized.
    if (!this.getChildren().isEmpty()) {
      // The identifier is unconditionally initialized because it's happening on the same line as its declaration. No
      // need to worry about other code branches where the identifier may not have been initialized yet.
      scopedHeap.initializeIdentifier(this.IDENTIFIER);

      res.append(
          String.format(
              " = %s", ((Expr) this.getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap).toString()));
    }
    res.append(";\n");

    return GeneratedJavaSource.forJavaSourceBody(res);
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    Type identifierValidatedType = optionalIdentifierDeclaredType.orElse(identifierValidatedInferredType);

    // Put the declared variable directly in the heap, with its computed value if initialized.
    if (this.getChildren().isEmpty()) {
      scopedHeap.putIdentifierValue(this.IDENTIFIER, identifierValidatedType);
    } else {
      scopedHeap.putIdentifierValue(
          this.IDENTIFIER, identifierValidatedType, this.getChildren().get(0).generateInterpretedOutput(scopedHeap));
      scopedHeap.initializeIdentifier(this.IDENTIFIER);
    }
    return null;
  }
}
