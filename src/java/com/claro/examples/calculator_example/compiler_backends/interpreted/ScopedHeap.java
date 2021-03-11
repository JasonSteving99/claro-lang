package com.claro.examples.calculator_example.compiler_backends.interpreted;

import com.claro.examples.calculator_example.CalculatorParserException;
import com.google.common.base.Preconditions;

import java.util.*;

public class ScopedHeap {

  private final Stack<HashMap<String, IdentifierData>> scopedHeapStack = new Stack<>();
  private boolean checkUnused = true;

  public void disableCheckUnused() {
    this.checkUnused = false;
  }

  // This should honestly only be used by the Target.INTERPRETED path where the values will actually be known to the
  // CompilerBackend itself.
  public Object getIdentifierValue(String identifier) throws CalculatorParserException {
    Optional<Integer> optionalIdentifierScopeLevel = findIdentifierScopeLevel(identifier);
    if (optionalIdentifierScopeLevel.isPresent()) {
      return scopedHeapStack.elementAt(optionalIdentifierScopeLevel.get()).get(identifier).interpretedValue;
    }
    throw new CalculatorParserException(String.format("No variable <%s> within the current scope!", identifier));
  }

  // This should honestly only be used by the Target.JAVA_SOURCE path where the values won't be known to the
  // CompilerBackend itself.
  public void putIdentifierValue(String identifier) {
    putIdentifierValue(identifier, null);
  }

  // This should honestly only be used by the Target.INTERPRETED path where the values will actually be known to the
  // CompilerBackend itself.
  public void putIdentifierValue(String identifier, Object value) {
    Optional<Integer> optionalIdentifierScopeLevel = findIdentifierScopeLevel(identifier);
    scopedHeapStack
        .elementAt(
            optionalIdentifierScopeLevel.orElse(scopedHeapStack.size() - 1))
        .put(identifier, new IdentifierData(value));
  }

  public void markIdentifierUsed(String identifier) {
    Optional<Integer> identifierScopeLevel = findIdentifierScopeLevel(identifier);
    Preconditions.checkArgument(
        identifierScopeLevel.isPresent(),
        "Internal Compiler Error: attempting to mark usage of an undeclared identifier."
    );
    scopedHeapStack.elementAt(identifierScopeLevel.get()).get(identifier).used = true;
  }

  public void enterNewScope() {
    scopedHeapStack.push(new HashMap<String, IdentifierData>());
  }

  public void exitCurrScope() {
    if (checkUnused) {
      checkAllIdentifiersInCurrScopeUsed();
    }
    scopedHeapStack.pop();
  }

  public boolean isIdentifierDeclared(String identifier) {
    return findIdentifierScopeLevel(identifier).isPresent();
  }

  private Optional<Integer> findIdentifierScopeLevel(String identifier) {
    // Start searching first in the innermost scope, that is, the last map in our stack.
    int scopeLevel = scopedHeapStack.size();
    while (--scopeLevel >= 0) {
      if (scopedHeapStack.elementAt(scopeLevel).containsKey(identifier)) {
        return Optional.of(scopeLevel);
      }
    }
    return Optional.empty(); // Not found.
  }

  private void checkAllIdentifiersInCurrScopeUsed() throws CalculatorParserException {
    HashSet<String> unusedSymbolSet = new HashSet<>();
    for (Map.Entry<String, IdentifierData> identifierEntry : scopedHeapStack.peek().entrySet()) {
      if (!identifierEntry.getValue().used) {
        unusedSymbolSet.add(identifierEntry.getKey());
      }
    }
    if (!unusedSymbolSet.isEmpty()) {
      throw new CalculatorParserException(
          String.format("Warning! The following declared symbols are unused! %s", unusedSymbolSet));
    }
  }

  private static class IdentifierData {
    // This value is only meaningful if the
    Object interpretedValue;
    // This should be set to True when this identifier is referenced.
    boolean used = false;

    public IdentifierData(Object interpretedValue) {
      this.interpretedValue = interpretedValue;
    }
  }
}
