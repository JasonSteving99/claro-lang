package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.CalculatorParserException;

import java.util.HashSet;

public class IdentifierReferenceTerm extends Term {

  private final String identifier;

  public IdentifierReferenceTerm(String identifier, HashSet<String> symbolSet, HashSet<String> usedSymbolSet) {
    // TODO(steving) Note this is happening on the way *up* the AST while building bottom-up during parsing stage...eventually this should happen on the way down after the full AST is constructed in order to better support nested scopes.
    // Make sure we check this will actually be a valid reference before we allow it.
    if (!symbolSet.contains(identifier)) {
      throw new CalculatorParserException(String.format("Referencing variable <%s> before assignment!", identifier));
    }

    // Hold onto the relevant data for code-gen later.
    this.identifier = identifier;

    // Mark that we're using this variable as a term, so that at the end of the program, we can warn about unused.
    usedSymbolSet.add(identifier);
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    return new StringBuilder(this.identifier);
  }
}
