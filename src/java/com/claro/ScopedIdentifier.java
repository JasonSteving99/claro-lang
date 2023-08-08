package com.claro;

import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.google.auto.value.AutoValue;

import java.util.Optional;
import java.util.function.Supplier;

@AutoValue
public abstract class ScopedIdentifier {
  public abstract String getScopeName();

  public abstract String getIdentifier();

  public abstract Supplier<String> getCurrentInputLine();

  public abstract int getLineNumber();

  public abstract int getStartCol();

  public static ScopedIdentifier forScopeNameAndIdentifier(
      String scopeName, String identifier, Supplier<String> currentInputLine, int lineNumber, int startCol) {
    return new AutoValue_ScopedIdentifier(scopeName, identifier, currentInputLine, lineNumber, startCol);
  }

  @Override
  public String toString() {
    return String.format("%s::%s", this.getScopeName(), this.getIdentifier());
  }

  public int getOverallLen() {
    return this.getScopeName().length() + "::".length() + this.getIdentifier().length();
  }

  public IdentifierReferenceTerm getScopeNameIdentifierReferenceTerm(Optional<String> optionalDefiningModuleDisambiguator) {
    return new IdentifierReferenceTerm(
        this.getScopeName(),
        optionalDefiningModuleDisambiguator,
        this.getCurrentInputLine(),
        this.getLineNumber(),
        this.getStartCol(),
        this.getStartCol() + this.getScopeName().length()
    );
  }

  public IdentifierReferenceTerm getIdentifierIdentifierReferenceTerm(Optional<String> optionalDefiningModuleDisambiguator) {
    return new IdentifierReferenceTerm(
        this.getIdentifier(),
        optionalDefiningModuleDisambiguator,
        this.getCurrentInputLine(),
        this.getLineNumber(),
        this.getStartCol() + this.getScopeName().length() + "::".length(),
        this.getStartCol() + this.getOverallLen()
    );
  }

  public IdentifierReferenceTerm getOverallIdentifierReferenceTerm(
      String disambiguatedIdentifierName, String definingModuleDisambiguator) {
    return new IdentifierReferenceTerm(
        disambiguatedIdentifierName,
        Optional.of(definingModuleDisambiguator),
        this.getCurrentInputLine(),
        this.getLineNumber(),
        this.getStartCol(),
        this.getStartCol() + this.getOverallLen()
    );
  }
}
