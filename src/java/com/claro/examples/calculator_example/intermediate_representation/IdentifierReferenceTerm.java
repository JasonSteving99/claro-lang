package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.CalculatorParserException;
import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;

public class IdentifierReferenceTerm extends Term {

  private final String identifier;

  public IdentifierReferenceTerm(String identifier) {
    // Hold onto the relevant data for code-gen later.
    this.identifier = identifier;
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Make sure we check this will actually be a valid reference before we allow it.
    if (scopedHeap.isIdentifierDeclared(identifier)) {
      scopedHeap.markIdentifierUsed(identifier);
      return new StringBuilder(this.identifier);
    }
    throw new CalculatorParserException(String.format("Referencing variable <%s> before assignment!", identifier));
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return scopedHeap.getIdentifierValue(this.identifier);
  }
}
