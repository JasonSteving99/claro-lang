package com.claro.examples.calculator_example.compiler_backends.interpreted;

import com.claro.examples.calculator_example.CalculatorParserException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;

import java.util.*;
import java.util.stream.Collectors;

// TODO(steving) There should be a ScopedSymbolTable interface with 2 impls. 1: ScopedHeap used for Interpreter and
// TODO(steving) 2: ScopedSymbolTableImpl for the compiler impl. B/c only Interpreter needs actual values stored.
public class ScopedHeap {

  private final Stack<Scope> scopeStack = new Stack<>();
  private boolean checkUnused = true;

  public void disableCheckUnused() {
    this.checkUnused = false;
  }

  // This should honestly only be used by the Target.INTERPRETED path where the values will actually be known to the
  // CompilerBackend itself.
  public Object getIdentifierValue(String identifier) throws CalculatorParserException {
    return getIdentifierData(identifier).interpretedValue;
  }

  public Type getValidatedIdentifierType(String identifier) {
    return getIdentifierData(identifier).type;
  }

  // This must only be used during the compiler's type discovery and validation phase before execution phase. This marks
  // the existence and type of an identifier so that its type may be depended on while validating types in successive
  // lines before execution.
  public void observeIdentifier(String identifier, Type type) {
    putIdentifierValue(identifier, type);
  }

  // This should honestly only be used by the Target.JAVA_SOURCE path where the values won't be known to the
  // CompilerBackend itself.
  public void declareIdentifier(String identifier) {
    getIdentifierData(identifier).declared = true;
  }

  // In order to perform branch detection of identifier initialization, we need to be able to only mark identifiers as
  // initialized on the scope level where they're actually init'd. Independent from setting its value which should still
  // be a side-effect that propogates up to the scope-level of declaration.
  public void initializeIdentifier(String identifier) {
    // Mark it initialized only in this current code branch represented by this current scope level.
    scopeStack.peek().initializedIdentifiers.add(identifier);
  }

  // This should honestly only be used by the Target.INTERPRETED path where the values will actually be known to the
  // CompilerBackend itself.
  public void putIdentifierValue(String identifier, Type type) {
    putIdentifierValue(identifier, type, null);
  }

  // This should honestly only be used by the Target.INTERPRETED path where the values will actually be known to the
  // CompilerBackend itself.
  public void putIdentifierValue(String identifier, Type type, Object value) {
    Optional<Integer> optionalIdentifierScopeLevel = findIdentifierDeclaredScopeLevel(identifier);
    scopeStack
        .elementAt(
            optionalIdentifierScopeLevel.orElse(scopeStack.size() - 1))
        .scopedHeap
        .put(identifier, new IdentifierData(type, value, true));
    if (value != null) {
      scopeStack.peek().initializedIdentifiers.add(identifier);
    }
  }

  // Simply update the value but don't change any other metadata associated with the symbol.
  public void updateIdentifierValue(String identifier, Object updatedIdentifierValue) {
    IdentifierData identifierData = getIdentifierData(identifier);
    identifierData.interpretedValue = updatedIdentifierValue;
  }

  public void markIdentifierUsed(String identifier) {
    Optional<Integer> identifierScopeLevel = findIdentifierDeclaredScopeLevel(identifier);
    Preconditions.checkArgument(
        identifierScopeLevel.isPresent(),
        "Internal Compiler Error: attempting to mark usage of an undeclared identifier."
    );
    scopeStack.elementAt(identifierScopeLevel.get()).scopedHeap.get(identifier).used = true;
  }

  // TODO(steving) For now, acknowledging that (enter|exit)NewScope() is actually *only* covering the case of
  // TODO(steving) *BRANCHING*. This is because there are no such things as classes or functions which would require
  // TODO(steving) expanded scoping rules.

  // For use only during the type-checking phase.
  public void observeNewScope(boolean beginIdentifierInitializationBranchInspection) {
    if (beginIdentifierInitializationBranchInspection) {
      scopeStack.peek().branchDetectionEnabled = true;
    }
    scopeStack.push(new Scope());
  }

  public void exitCurrObservedScope(boolean finalizeIdentifiersInitializedInBranchGroup) {
    if (checkUnused) {
      checkAllIdentifiersInCurrScopeUsed();
    }
    Scope exitedScope = scopeStack.pop();
    if (scopeStack.peek().branchDetectionEnabled) {
      scopeStack.peek().updateIdentifiersInitializedInBranchGroup(exitedScope);
      if (finalizeIdentifiersInitializedInBranchGroup) {
        scopeStack.peek().finalizeIdentifiersInitializedInBranchGroup();
      }
    }
  }

  public void enterNewScope() {
    scopeStack.push(new Scope());
  }

  public void exitCurrScope() {
    if (checkUnused) {
      checkAllIdentifiersInCurrScopeUsed();
    }
    scopeStack.pop();
  }

  public boolean isIdentifierDeclared(String identifier) {
    Optional<Integer> identifierScopeLevel = findIdentifierDeclaredScopeLevel(identifier);
    return
        identifierScopeLevel.isPresent() &&
        scopeStack.elementAt(identifierScopeLevel.get()).scopedHeap.get(identifier).declared;
  }

  public boolean isIdentifierInitialized(String identifier) {
    return findIdentifierInitializedScopeLevel(identifier).isPresent();
  }

  private IdentifierData getIdentifierData(String identifier) throws CalculatorParserException {
    Optional<Integer> optionalIdentifierScopeLevel = findIdentifierDeclaredScopeLevel(identifier);
    if (optionalIdentifierScopeLevel.isPresent()) {
      return scopeStack.elementAt(optionalIdentifierScopeLevel.get()).scopedHeap.get(identifier);
    }
    throw new CalculatorParserException(String.format("No variable <%s> within the current scope!", identifier));

  }

  private Optional<Integer> findIdentifierDeclaredScopeLevel(String identifier) {
    return findScopeStackLevel(scopeLevel -> scopeLevel.scopedHeap.containsKey(identifier));
  }

  private Optional<Integer> findIdentifierInitializedScopeLevel(String identifier) {
    return findScopeStackLevel(scopeLevel -> scopeLevel.initializedIdentifiers.contains(identifier));
  }

  private Optional<Integer> findScopeStackLevel(Function<Scope, Boolean> checkScopeLevelFn) {
    // Start searching first in the innermost scope, that is, the last map in our stack.
    int scopeLevel = scopeStack.size();
    while (--scopeLevel >= 0) {
      if (checkScopeLevelFn.apply(scopeStack.elementAt(scopeLevel))) {
        return Optional.of(scopeLevel);
      }
    }
    return Optional.empty(); // Not found.
  }

  private void checkAllIdentifiersInCurrScopeUsed() throws CalculatorParserException {
    HashSet<String> unusedSymbolSet = new HashSet<>();
    for (Map.Entry<String, IdentifierData> identifierEntry : scopeStack.peek().scopedHeap.entrySet()) {
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
    Type type;
    // This value is only meaningful in interpreted modes where values are tracked.
    Object interpretedValue;
    // This should be set to True when this identifier is referenced.
    boolean used = false;
    // This should be set when this identifier is first observed to during the compiler's type checking phase.
    boolean declared;

    public IdentifierData(Type type, Object interpretedValue) {
      this(type, interpretedValue, false);
    }

    public IdentifierData(Type type, Object interpretedValue, boolean declared) {
      this.type = type;
      this.interpretedValue = interpretedValue;
      this.declared = true;
    }
  }

  private static class Scope {
    // This is a map that contains all declared identifiers. An entry will be made in this map for every identifier
    // immediately following its declaration. Its type and value will be logged at the first scope level where it was
    // declared because further code branches (scopes) may still need to update its value as a desired side-effect.
    final HashMap<String, IdentifierData> scopedHeap = new HashMap<>();

    // This is a set containing all identifiers that have been initialized at this current scope level for the first
    // time along the given code branch (AST subtree) corresponding to this current scope level. This exists separate
    // from the above scopedHeap so that we're able to separately identify whether it's valid to reference a certain
    // identifier within certain code branches where the identifier may have been originally declared without an
    // initializer value.
    final HashSet<String> initializedIdentifiers = new HashSet<>();

    boolean branchDetectionEnabled = false;
    private HashSet<String> identifiersInitializedInBranchGroup = null;

    /*
     * The below code handling code branches allows us to do branch inspection to determine at compile-time whether we
     * can guarantee that an identifier will have been initialized after exiting a branch group. An example would be
     * the following code:
     *
     * ```
     * var x: int;
     * if (foo()) {
     *   x = 1;
     * } else {
     *   x = 2;
     * }
     * print(x);
     * ```
     *
     * Because x is initialized in every branch, and there is a guarantee that at least one branch will execute, then we
     * know that at the end of that branch group (if-else chain) then the identifier x will be initialized.
     *
     * Note that this hinges upon knowing that at least one branch in the group will certainly execute. In the case of
     * the if-else chain, then it's necessary that there's an else clause present for this branch inspection to be
     * worthwhile. The onus is on the ScopedHeap callers to ensure they only request branch inspection if they know that
     * it may be valid (since the caller is the one with domain knowledge on whether one of the branches will execute).
     * */

    void updateIdentifiersInitializedInBranchGroup(Scope branchScope) {
      // Filter out the identifiers that were actually declared in the given branchScope since those aren't known at
      // this current Scope.
      HashSet<String> knownIdentifiersInitializedInBranchGroup = branchScope.initializedIdentifiers.stream()
          // If the inititializedIdentifier is found in the branchScope's scopedHeap, then it was declared at that
          // lower scope-level and we're not concerned with it.
          .filter(initializedIdentifier -> !branchScope.scopedHeap.containsKey(initializedIdentifier))
          .collect(Collectors.toCollection(HashSet::new));

      if (identifiersInitializedInBranchGroup == null) {
        identifiersInitializedInBranchGroup = knownIdentifiersInitializedInBranchGroup;
      } else {
        this.identifiersInitializedInBranchGroup.retainAll(knownIdentifiersInitializedInBranchGroup);
      }
    }

    void finalizeIdentifiersInitializedInBranchGroup() {
      initializedIdentifiers.addAll(identifiersInitializedInBranchGroup);
      branchDetectionEnabled = false;
      identifiersInitializedInBranchGroup = null;
    }
  }
}
