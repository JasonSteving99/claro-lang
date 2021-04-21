package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.google.common.base.Preconditions;

public class IdentifierReferenceTerm extends Term {

  private final String identifier;

  public IdentifierReferenceTerm(String identifier) {
    // Hold onto the relevant data for code-gen later.
    this.identifier = identifier;
  }

  public String getIdentifier() {
    return identifier;
  }

  @Override
  protected Type getValidatedExprType(ScopedHeap scopedHeap) {
    // Make sure we check this will actually be a valid reference before we allow it.
    Preconditions.checkState(
        scopedHeap.isIdentifierDeclared(this.identifier),
        "No variable <%s> within the current scope!",
        this.identifier
    );
    Preconditions.checkState(
        scopedHeap.isIdentifierInitialized(this.identifier),
        "Variable <%s> may not have been initialized!",
        this.identifier
    );
    return scopedHeap.getValidatedIdentifierType(this.identifier);
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    scopedHeap.markIdentifierUsed(identifier);
    return new StringBuilder(this.identifier);
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return scopedHeap.getIdentifierValue(this.identifier);
  }
}
