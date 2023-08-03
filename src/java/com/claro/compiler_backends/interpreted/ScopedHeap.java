package com.claro.compiler_backends.interpreted;

import com.claro.ClaroParserException;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.Type;
import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// TODO(steving) There should be a ScopedSymbolTable interface with 2 impls. 1: ScopedHeap used for Interpreter and
// TODO(steving) 2: ScopedSymbolTableImpl for the compiler impl. B/c only Interpreter needs actual values stored.
public class ScopedHeap {

  @VisibleForTesting
  public final Stack<Scope> scopeStack = new Stack<>();
  public boolean checkUnused = true;
  // A table<depModuleName, isUsed, descriptor> of dep module descriptors and whether or not they have been referenced.
  public static HashBasedTable<String, Boolean, SerializedClaroModule.UniqueModuleDescriptor> currProgramDepModules =
      HashBasedTable.create();
  public static ImmutableMap<String, SerializedClaroModule.ExportedTypeDefinitions> currProgramDepModuleExportedTypes =
      ImmutableMap.of();
  // The set of Dep Modules that are listed as transitive exports of this module. Dep Modules are required to be
  // explicitly placed here via claro_module(..., exports = ["Foo"], deps = {"Foo": "//..."}) if the
  // .claro_module_api file is going to explicitly reference any types declared in a dep Module (e.g. `Foo::FooType`).
  // This ensures that the consumers of this Module are actually able to read the definition of the transitive dep types.
  public static ImmutableSet<String> transitiveExportedDepModules = ImmutableSet.of();

  public static String getDefiningModuleDisambiguator(Optional<String> optionalOriginatingDepModuleName) {
    String res;
    if (optionalOriginatingDepModuleName.isPresent()) {
      // Just assume that the dep module was correctly registered but may or may not be marked used yet.
      res = ScopedHeap.currProgramDepModules.rowMap().get(optionalOriginatingDepModuleName.get())
          .values().stream().findFirst().get().getUniqueModuleName();
    } else {
      Optional<SerializedClaroModule.UniqueModuleDescriptor> thisModuleDesc;
      if ((thisModuleDesc =
               Optional.ofNullable(ScopedHeap.currProgramDepModules.rowMap().get("$THIS_MODULE$"))
                   .map(m -> m.values().stream().findFirst().get())).isPresent()) {
        res = thisModuleDesc.get().getUniqueModuleName();
      } else {
        // Turns out this is not defined within a module (it's just a top-level src in a claro_binary()) so since nobody
        // else can depend on this, we don't need any disambiguator.
        res = "";
      }
    }
    return res;
  }

  public static Optional<String> getModuleNameFromDisambiguator(String disambiguator) {
    if (disambiguator.isEmpty()) {
      return Optional.empty();
    }
    return ScopedHeap.currProgramDepModules.cellSet().stream()
        .filter(c -> c.getValue().getUniqueModuleName().equals(disambiguator))
        .findFirst()
        .map(Table.Cell::getRowKey);
  }

  public void disableCheckUnused() {
    this.checkUnused = false;
  }

  // This should honestly only be used by the Target.INTERPRETED path where the values will actually be known to the
  // CompilerBackend itself.
  public Object getIdentifierValue(String identifier) throws ClaroParserException {
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

  public void observeIdentifierAllowingHiding(String identifier, Type type) {
    putIdentifierValueAllowingHiding(identifier, type, null);
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

  public void markIdentifierAsTypeDefinition(String identifier) {
    findIdentifierDeclaredScopeLevel(identifier).ifPresent(
        level -> scopeStack.get(level).scopedSymbolTable.get(identifier).isTypeDefinition = true
    );
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
    putIdentifierValueAtLevel(
        identifier, type, value, optionalIdentifierScopeLevel.orElse(scopeStack.size() - 1));
  }

  public void putIdentifierValueAsTypeDef(String identifier, Type type, Object value) {
    Optional<Integer> optionalIdentifierScopeLevel = findIdentifierDeclaredScopeLevel(identifier);
    putIdentifierValueAtLevel(
        identifier, type, value, optionalIdentifierScopeLevel.orElse(scopeStack.size() - 1), /*isTypeDefinition=*/true);
  }

  public void putIdentifierValueAtLevel(String identifier, Type type, Object value, int scopeLevel) {
    putIdentifierValueAtLevel(identifier, type, value, scopeLevel, /*isTypeDefinition=*/false);
  }

  public void putIdentifierValueAtLevel(String identifier, Type type, Object value, int scopeLevel, boolean isTypeDefinition) {
    IdentifierData identifierData = new IdentifierData(type, value, true);
    if (isTypeDefinition) {
      identifierData.isTypeDefinition = true;
    }
    scopeStack
        .elementAt(scopeLevel)
        .scopedSymbolTable
        .put(identifier, identifierData);
    if (value != null) {
      scopeStack.peek().initializedIdentifiers.add(identifier);
    }
  }

  // This is the same as the above ScopedHeap::putIdentifierValue, with the difference that it allows variable
  // hiding. I.e. if there is already a variable defined in an outer scope named `foo`, this method allows the
  // declaration of another new variable named `foo` in an inner scope that will hide the outer variable from
  // references within this scope since searching for declared identifiers will stop at the first found in a
  // bottom-up search.
  public void putIdentifierValueAllowingHiding(String identifier, Type type, Object value) {
    scopeStack
        .peek()
        .scopedSymbolTable
        .put(identifier, new IdentifierData(type, value, true));
    if (value != null) {
      scopeStack.peek().initializedIdentifiers.add(identifier);
    }
  }

  // Put a copy of an existing identifier's IdentifierData in the current Scope. This should be used for
  // intentional hiding of captured variables (e.g. for a lambda implicitly capturing outer variables).
  public void putCapturedIdentifierData(Map<String, IdentifierData> lambdaScopeCapturedIdentifierData) {
    lambdaScopeCapturedIdentifierData.forEach(
        (identifier, identifierData) ->
            scopeStack
                .peek()
                .scopedSymbolTable
                .put(identifier, identifierData));
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
        String.format(
            "Internal Compiler Error: attempting to mark usage of an undeclared identifier %s.",
            identifier
        )
    );
    scopeStack.elementAt(identifierScopeLevel.get()).scopedSymbolTable.get(identifier).used = true;
  }

  public static void markDepModuleUsed(String depModule) {
    // Don't need to do anything if this module's already been marked used.
    if (ScopedHeap.currProgramDepModules.contains(depModule, /*isUsed=*/false)) {
      Map<Boolean, SerializedClaroModule.UniqueModuleDescriptor> depModuleRowMap =
          ScopedHeap.currProgramDepModules.rowMap().get(depModule);
      depModuleRowMap.put(/*isUsed=*/true, depModuleRowMap.get(/*isUsed=*/false));
      depModuleRowMap.remove(/*isUsed=*/false);
    }
  }

  public void deleteIdentifierValue(String identifier) {
    Optional<Integer> optionalIdentifierScopeLevel = findIdentifierDeclaredScopeLevel(identifier);
    optionalIdentifierScopeLevel.ifPresent(
        scopeLevel -> scopeStack.elementAt(scopeLevel).scopedSymbolTable.remove(identifier)
    );
  }

  // For use only during the type-checking phase.
  public void observeNewScope(boolean beginIdentifierInitializationBranchInspection) {
    observeNewScope(beginIdentifierInitializationBranchInspection, Scope.ScopeType.DEFAULT_SCOPE);
  }

  // For use only during the type-checking phase.
  public void observeNewScope(boolean beginIdentifierInitializationBranchInspection, Scope.ScopeType scopeType) {
    if (beginIdentifierInitializationBranchInspection) {
      scopeStack.peek().branchDetectionEnabled = true;
    }
    scopeStack.push(new Scope(scopeType));
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
    enterNewScope(Scope.ScopeType.DEFAULT_SCOPE);
  }

  public void enterNewScope(Scope.ScopeType scopeType) {
    scopeStack.push(new Scope(scopeType));
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
        scopeStack.elementAt(identifierScopeLevel.get()).scopedSymbolTable.get(identifier).declared;
  }

  public boolean isIdentifierInitialized(String identifier) {
    return findIdentifierInitializedScopeLevel(identifier).isPresent();
  }

  public IdentifierData getIdentifierData(String identifier) throws ClaroParserException {
    Optional<Integer> optionalIdentifierScopeLevel = findIdentifierDeclaredScopeLevel(identifier);
    if (optionalIdentifierScopeLevel.isPresent()) {
      return scopeStack.elementAt(optionalIdentifierScopeLevel.get()).scopedSymbolTable.get(identifier);
    }
    throw new ClaroParserException(String.format("No identifier <%s> within the current scope!", identifier));
  }

  private Optional<Integer> findIdentifierDeclaredScopeLevel(String identifier) {
    return findScopeStackLevel(scopeLevel -> scopeLevel.scopedSymbolTable.containsKey(identifier), identifier);
  }

  public Optional<Integer> findIdentifierInitializedScopeLevel(String identifier) {
    return findScopeStackLevel(scopeLevel -> scopeLevel.initializedIdentifiers.contains(identifier), identifier);
  }

  // This method searches from the current Scope outwards, trying to find any scope that matches the given
  // predicate. This method also takes responsibility of honoring the unique Scoping rules applicable to
  // each Scope.ScopeType so this method's behavior differs based on the ScopeType of the current (and outer)
  // Scopes to simulate visibility rules. Namely, once a FUNCTION_SCOPE is reached, it will ignore any
  // non-Function-Type (or non-Contract-Type) identifiers that it finds outside.
  private Optional<Integer> findScopeStackLevel(Function<Scope, Boolean> checkScopeLevelFn, String identifier) {
    // Start searching first in the innermost scope, that is, the last map in our stack.
    int scopeLevel = scopeStack.size();
    Optional<Integer> pastFunctionScopeBoundary = Optional.empty();
    Optional<Integer> pastLambdaScopeBoundary = Optional.empty();
    while (--scopeLevel >= 0) {
      Scope currScope = scopeStack.elementAt(scopeLevel);
      Scope.ScopeType currScopeType = currScope.scopeType;
      boolean checkScopeLevelFnResult = checkScopeLevelFn.apply(currScope);
      switch (currScopeType) {
        case DEFAULT_SCOPE:
          if (checkScopeLevelFnResult) {
            if (pastFunctionScopeBoundary.isPresent() || pastLambdaScopeBoundary.isPresent()) {
              ImmutableSet<BaseType> functionBaseTypes =
                  ImmutableSet.of(BaseType.FUNCTION, BaseType.CONSUMER_FUNCTION, BaseType.PROVIDER_FUNCTION);
              IdentifierData identifierData = currScope.scopedSymbolTable.get(identifier);
              if (identifierData.isTypeDefinition
                  || (functionBaseTypes.contains(identifierData.type.baseType()) &&
                      functionBaseTypes.contains(identifierData.type.getPossiblyOverridenBaseType()))) {
                // In either of these cases, we should accept Function type references and Type definitions.
                return Optional.of(scopeLevel);
              } else if (pastFunctionScopeBoundary.isPresent()) {
                // Functions may also reference Modules or Contracts defined in outer scopes.
                ImmutableSet<BaseType> allowedNonFunctionTypeReferences =
                    ImmutableSet.of(BaseType.MODULE, BaseType.$CONTRACT, BaseType.$CONTRACT_IMPLEMENTATION);
                if (allowedNonFunctionTypeReferences.contains(identifierData.type.baseType())) {
                  return Optional.of(scopeLevel);
                }
                return Optional.empty();
              } else {
                // Lambdas can reference anything in outer scopes, but they need to re-declare a hiding variable
                // copying the value from the outer scope. This method can handle that implicitly here adding
                // a new hiding identifier at the scope level that the lambda was found at.
                ImmutableSet<BaseType> ignoredIdentifierReferenceTypes =
                    ImmutableSet.of(BaseType.$CONTRACT, BaseType.$CONTRACT_IMPLEMENTATION);
                if (ignoredIdentifierReferenceTypes.contains(identifierData.type.baseType())) {
                  // In this case we actually will allow the lambda to reference the original identifiers in the default
                  // scope since we don't want lambdas to have to redeclare contracts.
                  return Optional.of(scopeLevel);
                } else {
                  redeclareCaptureVariable(identifier, pastLambdaScopeBoundary.get(), identifierData);

                  // Point the caller to the newly declared value rather than the one found in the outer scope
                  // since that's been hidden and copied now.
                  return pastLambdaScopeBoundary;
                }
              }
            } else {
              return Optional.of(scopeLevel);
            }
          }
          break;
        case FUNCTION_SCOPE:
          if (checkScopeLevelFnResult) {
            // Lambdas can reference anything in outer scopes, but they need to re-declare a hiding variable
            // copying the value from the outer scope. This method can handle that implicitly here adding
            // a new hiding identifier at the scope level that the lambda was found at.
            pastLambdaScopeBoundary.ifPresent(
                lambdaScopeLevel -> {
                  ImmutableSet<BaseType> ignoredIdentifierReferenceTypes =
                      ImmutableSet.of(BaseType.$CONTRACT, BaseType.$CONTRACT_IMPLEMENTATION);
                  if (!ignoredIdentifierReferenceTypes.contains(currScope.scopedSymbolTable.get(identifier).type.baseType())) {
                    redeclareCaptureVariable(
                        identifier, lambdaScopeLevel, currScope.scopedSymbolTable.get(identifier));
                  }
                });
            return Optional.of(scopeLevel);
          } else {
            // If we don't find what we're looking for before going outside the Function scope boundary,
            // we have to mark that we just did that. Let's just make sure that we keep only the first one
            // we come across in case I ever want to support nested Functions.
            if (!pastFunctionScopeBoundary.isPresent()) {
              pastFunctionScopeBoundary = Optional.of(scopeLevel);
            }
          }
          break;
        case LAMBDA_SCOPE:
          if (checkScopeLevelFnResult) {
            // Lambdas can reference anything in outer scopes, but they need to re-declare a hiding variable
            // copying the value from the outer scope. This method can handle that implicitly here adding
            // a new hiding identifier at the scope level that the lambda was found at.
            pastLambdaScopeBoundary.ifPresent(
                lambdaScopeLevel -> {
                  ImmutableSet<BaseType> ignoredIdentifierReferenceTypes =
                      ImmutableSet.of(BaseType.$CONTRACT, BaseType.$CONTRACT_IMPLEMENTATION);
                  if (!ignoredIdentifierReferenceTypes.contains(currScope.scopedSymbolTable.get(identifier).type.baseType())) {
                    redeclareCaptureVariable(
                        identifier, lambdaScopeLevel, currScope.scopedSymbolTable.get(identifier));
                  }
                });
            return Optional.of(scopeLevel);
          } else {
            // If we don't find what we're looking for before going outside the Lambda scope boundary,
            // we have to mark that we just did that. Let's just make sure that we keep only the first one
            // we come across.
            if (!pastLambdaScopeBoundary.isPresent()) {
              pastLambdaScopeBoundary = Optional.of(scopeLevel);
            }
          }
          break;
        default:
          throw new ClaroParserException("Internal Compiler Error: Unsupported ScopeType " + currScopeType);
      }
    }
    return Optional.empty(); // Not found.
  }

  // Need to re-declare a hiding variable copying the value from the outer scope. This method can
  // handle that implicitly here adding a new hiding identifier at the requested scope level.
  private void redeclareCaptureVariable(String identifier, int scopeLevel, IdentifierData identifierData) {
    IdentifierData redeclaredCaptureIdentifierData =
        new IdentifierData(identifierData.type, identifierData.interpretedValue, identifierData.declared);
    scopeStack.get(scopeLevel)
        .scopedSymbolTable
        .put(identifier, redeclaredCaptureIdentifierData);
    // I need to mark this newly initialized capture variable.
    scopeStack.get(scopeLevel).lambdaScopeCapturedVariables.put(identifier, identifierData.type);
    // That implicit copy that we just did, definitely counts as "using" the identifier.
    identifierData.used = true;
    redeclaredCaptureIdentifierData.used = true;
  }

  public void checkAllIdentifiersInCurrScopeUsed() throws ClaroParserException {
    HashSet<String> unusedSymbolSet = Sets.newHashSet();
    // First, I'll check for any unused variables.
    for (Map.Entry<String, IdentifierData> identifierEntry : scopeStack.peek().scopedSymbolTable.entrySet()) {
      if (!identifierEntry.getValue().used) {
        unusedSymbolSet.add(identifierEntry.getKey());
      }
    }
    if (!unusedSymbolSet.isEmpty()) {
      throw new ClaroParserException(
          String.format("Warning! The following declared symbols are unused! %s", unusedSymbolSet));
    }
  }

  public static void checkAllDepModulesUsed() {
    // I'll check for unused declared module dependencies, as this is technically just going to make the build
    // less performant for no reason.
    HashSet<String> unusedSymbolSet = Sets.newHashSet();
    for (String depModuleName : ScopedHeap.currProgramDepModules.rowKeySet()) {
      if (ScopedHeap.currProgramDepModules.contains(depModuleName, /*isUsed=*/false)) {
        unusedSymbolSet.add(depModuleName);
      }
    }
    if (!unusedSymbolSet.isEmpty()) {
      throw new ClaroParserException(
          String.format(
              "The following declared Module dependencies are unused! These build deps provide no value, and would " +
              "reduce your build performance. Remove them from your build target, or use them.\n\t- %s",
              String.join("\n\t- ", unusedSymbolSet)
          ));
    }
  }

  public static class IdentifierData {
    public Type type;
    // This value is only meaningful in interpreted modes where values are tracked.
    public Object interpretedValue;
    // This should be set to True when this identifier is referenced.
    public boolean used = false;
    // This should be set when this identifier is first observed to during the compiler's type checking phase.
    boolean declared;
    public boolean isTypeDefinition;

    public IdentifierData(Type type, Object interpretedValue) {
      this(type, interpretedValue, false);
    }

    public IdentifierData(Type type, Object interpretedValue, boolean declared) {
      this.type = type;
      this.interpretedValue = interpretedValue;
      this.declared = declared;
    }

    public IdentifierData getShallowCopy() {
      IdentifierData shallowCopy = new IdentifierData(this.type, this.interpretedValue, this.declared);
      shallowCopy.used = this.used;
      shallowCopy.isTypeDefinition = this.isTypeDefinition;
      return shallowCopy;
    }

    @Override
    public String toString() {
      StringBuilder res = new StringBuilder("IdentifierData(");

      res.append("type = ").append(this.type).append(", ");
      res.append("interpretedValue = ").append(this.interpretedValue).append(", ");
      res.append("used = ").append(this.used).append(", ");
      res.append("declared = ").append(this.declared).append(", ");
      res.append("isTypeDefinition = ").append(this.isTypeDefinition);

      return res.append(")").toString();
    }
  }

  @VisibleForTesting
  public static class Scope {
    // This is a map that contains all declared identifiers. An entry will be made in this map for every identifier
    // immediately following its declaration. Its type and value will be logged at the first scope level where it was
    // declared because further code branches (scopes) may still need to update its value as a desired side-effect.
    @VisibleForTesting
    public final HashMap<String, IdentifierData> scopedSymbolTable = new HashMap<>();

    // This is a set containing all identifiers that have been initialized at this current scope level for the first
    // time along the given code branch (AST subtree) corresponding to this current scope level. This exists separate
    // from the above scopedSymbolTable so that we're able to separately identify whether it's valid to reference a certain
    // identifier within certain code branches where the identifier may have been originally declared without an
    // initializer value.
    @VisibleForTesting
    public final HashSet<String> initializedIdentifiers = new HashSet<>();

    boolean branchDetectionEnabled = false;
    private HashSet<String> identifiersInitializedInBranchGroup = null;

    // This set of identifiers is intended to track all identifiers that were dynamically added to this Scope
    // for the sake of "capturing" the current state of a variable, and "hiding" the variable itself.
    public HashMap<String, Type> lambdaScopeCapturedVariables = Maps.newHashMap();

    // Track whether this represents the top-level of a Function or Lambda Scope (or in the future a Method Scope).
    // There are certain special limitations on what may or may not be accessed from outer scopes in these cases.
    public enum ScopeType {
      DEFAULT_SCOPE, // In this scope, there is free access to outer scopes (restrictions may be applied by scopes above).
      FUNCTION_SCOPE, // NO ACCESS to anything in the outer scope WITH THE EXCEPTION of other procedures.
      LAMBDA_SCOPE, // FREE ACCESS to anything in the outer scope, but SHOULD MAKE COPIES of all non-procedures.
    }

    final ScopeType scopeType;

    Scope(ScopeType scopeType) {
      this.scopeType = scopeType;
    }

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
          // If the inititializedIdentifier is found in the branchScope's scopedSymbolTable, then it was declared at that
          // lower scope-level and we're not concerned with it.
          .filter(initializedIdentifier -> !branchScope.scopedSymbolTable.containsKey(initializedIdentifier))
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

  @Override
  public String toString() {
    StringBuilder res = new StringBuilder();

    int i = scopeStack.size();
    while (--i >= 0) {
      res.append(String.format("Scope Level: %s\n", i));
      res.append("scopedSymbolTable: " + scopeStack.get(i).scopedSymbolTable.entrySet());
      res.append("\n");
      res.append("initializedIdentifiers: " + scopeStack.get(i).initializedIdentifiers);
      res.append("\n\n");
    }

    return res.toString();
  }
}
