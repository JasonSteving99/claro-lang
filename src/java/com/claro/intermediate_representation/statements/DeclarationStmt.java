package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.Optional;

public class DeclarationStmt extends Stmt {

  private final String IDENTIFIER;

  // Only oneof these should be set.
  private final Optional<TypeProvider> optionalIdentifierDeclaredTypeProvider;
  private Type identifierValidatedInferredType;

  // Determines whether this variable declaration should allow variable hiding or not. This is not always desirable,
  // so it must be explicitly set if this is desirable in this case.
  private final boolean allowVariableHiding;

  // Constructor for var initialization requesting type inference.
  public DeclarationStmt(String identifier, Expr e) {
    super(ImmutableList.of(e));
    this.IDENTIFIER = identifier;
    this.optionalIdentifierDeclaredTypeProvider = Optional.empty();
    this.allowVariableHiding = false;
  }

  // Allow typed declarations with initialization.
  public DeclarationStmt(String identifier, TypeProvider declaredTypeProvider, Expr e) {
    super(ImmutableList.of(e));
    this.IDENTIFIER = identifier;
    this.optionalIdentifierDeclaredTypeProvider = Optional.of(declaredTypeProvider);
    this.allowVariableHiding = false;
  }

  // Allow typed declarations with initialization that can hide variables in outer scopes.
  public DeclarationStmt(String identifier, TypeProvider declaredTypeProvider, Expr e, boolean allowVariableHiding) {
    super(ImmutableList.of(e));
    this.IDENTIFIER = identifier;
    this.optionalIdentifierDeclaredTypeProvider = Optional.of(declaredTypeProvider);
    this.allowVariableHiding = allowVariableHiding;
  }

  // Allow typed declarations without initialization.
  public DeclarationStmt(String identifier, TypeProvider declaredTypeProvider) {
    super(ImmutableList.of());
    this.IDENTIFIER = identifier;
    this.optionalIdentifierDeclaredTypeProvider = Optional.of(declaredTypeProvider);
    this.allowVariableHiding = false;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Validate that this is not a redeclaration of an identifier.
    Preconditions.checkState(
        !scopedHeap.isIdentifierDeclared(this.IDENTIFIER),
        String.format("Unexpected redeclaration of identifier <%s>.", this.IDENTIFIER)
    );

    // Determine which type this identifier was declared as, validating initializer Expr as necessary.
    if (optionalIdentifierDeclaredTypeProvider.isPresent()) {
      if (!this.getChildren().isEmpty()) {
        ((Expr) this.getChildren().get(0))
            .assertExpectedExprType(scopedHeap, optionalIdentifierDeclaredTypeProvider.get().resolveType(scopedHeap));
        scopedHeap.initializeIdentifier(this.IDENTIFIER);
      }
      scopedHeap.observeIdentifier(this.IDENTIFIER, optionalIdentifierDeclaredTypeProvider.get()
          .resolveType(scopedHeap));
    } else {
      // Infer the identifier's type only the first time it's assigned to.
      this.identifierValidatedInferredType = ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap);
      scopedHeap.observeIdentifier(this.IDENTIFIER, identifierValidatedInferredType);
      scopedHeap.initializeIdentifier(this.IDENTIFIER);
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder res = new StringBuilder();

    Type identifierValidatedType =
        optionalIdentifierDeclaredTypeProvider.orElse((unused) -> identifierValidatedInferredType)
            .resolveType(scopedHeap);

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
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    Type identifierValidatedType =
        optionalIdentifierDeclaredTypeProvider.orElse((unused) -> identifierValidatedInferredType)
            .resolveType(scopedHeap);

    // Put the declared variable directly in the heap, with its computed value if initialized.
    if (this.getChildren().isEmpty()) {
      scopedHeap.putIdentifierValue(this.IDENTIFIER, identifierValidatedType);
    } else {
      if (allowVariableHiding) {
        scopedHeap.putIdentifierValueAllowingHiding(
            this.IDENTIFIER, identifierValidatedType, this.getChildren().get(0).generateInterpretedOutput(scopedHeap));
      } else {
        scopedHeap.putIdentifierValue(
            this.IDENTIFIER, identifierValidatedType, this.getChildren().get(0).generateInterpretedOutput(scopedHeap));
      }
      scopedHeap.initializeIdentifier(this.IDENTIFIER);
    }
    return null;
  }
}
