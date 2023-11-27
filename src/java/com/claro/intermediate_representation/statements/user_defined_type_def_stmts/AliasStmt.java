package com.claro.intermediate_representation.statements.user_defined_type_def_stmts;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.Optional;

public class AliasStmt extends Stmt implements UserDefinedTypeDefinitionStmt {
  public static final String CURR_ALIAS_DEF_NAME = "$CURR_ALIAS_DEF_NAME";
  public final String alias;
  private final TypeProvider aliasedType;
  public Type resolvedType;

  public AliasStmt(String alias, TypeProvider aliasedType) {
    super(ImmutableList.of());
    this.alias = alias;
    this.aliasedType = aliasedType;
  }

  @Override
  public void registerTypeProvider(ScopedHeap scopedHeap) {
    // Validate that this is not a redeclaration of an identifier.
    Preconditions.checkState(
        !scopedHeap.isIdentifierDeclared(this.alias),
        String.format("Unexpected redeclaration of %s.", this.alias)
    );

    // Register a null type since it's not yet resolved, and then abuse its Object value field temporarily to hold the
    // TypeProvider that will be used for type-resolution in the later phase
    scopedHeap.putIdentifierValue(
        this.alias,
        null,
        (TypeProvider) (scopedHeap1) -> {
          // In order to handle potential recursive alias definitions, we need to be able to track whether or not this
          // particular alias definition is recursive, which requires knowing the name of the current alias. I have to
          // track the previous to reset it after, because it's actually possible, based on the TypeProvider resolution
          // scheme we're following, that one alias definition will be interrupted by resolving another alias definition
          // midway through.
          Optional<String> originalAliasName =
              scopedHeap1.isIdentifierDeclared(CURR_ALIAS_DEF_NAME)
              ? Optional.of((String) scopedHeap1.getIdentifierValue(CURR_ALIAS_DEF_NAME))
              : Optional.empty();
          scopedHeap1.putIdentifierValue(
              CURR_ALIAS_DEF_NAME,
              null,
              this.alias
          );

          // Core type resolution.
          Type resolvedType = aliasedType.resolveType(scopedHeap1);

          // If an AliasSelfReference was found that was not nested within some collection type with an implicit
          // "bottom" to terminate the type recursion, then this is an "impossible" type definition that could never be
          // initialized in a finite number of steps (for the programmer - they literally couldn't finish typing it in).
          // TODO(steving) Consider implementing an "init block" construct within which all types are allowed to use
          //  `null` as an implicit "bottom". This would allow the initialization of infinitely recursive types in
          //  finite steps, by allowing the programmer to defer setting a value. This would be difficult for Claro to
          //  support because it would require automatically verifying that every field that was initially set to `null`
          //  is eventually guaranteed to be assigned a non-`null` value by the end of the "init block" and also tracking
          //  that `null` values certainly never leave the block.
          //  E.g.:
          //    alias CircularType : tuple<string, CircularType>
          //    var c1: CircularType;
          //    var c2: CircularType;
          //    init c1, c2 {
          //      c1 = ("Amy", null);
          //      c2 = ("Bob", c1);
          //      c1[1] = c2;
          //    }
          if (scopedHeap1.isIdentifierDeclared("$POTENTIAL_IMPOSSIBLE_SELF_REFERENCING_TYPE_FOUND")) {
            throw new RuntimeException(ClaroTypeException.forImpossibleRecursiveAliasTypeDefinition(this.alias));
            // TODO(steving) Allow the error to be logged and continued. Reset so the next alias may be checked.
//            scopedHeap1.deleteIdentifierValue("$POTENTIAL_IMPOSSIBLE_SELF_REFERENCING_TYPE_FOUND");
          }

          // Reset initial state.
          if (originalAliasName.isPresent()) {
            scopedHeap1.updateIdentifierValue(CURR_ALIAS_DEF_NAME, originalAliasName.get());
          } else {
            scopedHeap1.deleteIdentifierValue(CURR_ALIAS_DEF_NAME);
          }
          return resolvedType;
        }
    );
    scopedHeap.markIdentifierAsTypeDefinition(this.alias);
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // In order to allow users to define aliases within arbitrary scopes, I want to simply check whether the type is
    // already registered, it may not be in the case that this is nested in some function definition and the type
    // registration pass didn't notice this alias def.
    if (!scopedHeap.isIdentifierDeclared(this.alias)) {
      registerTypeProvider(scopedHeap);
    }

    // The only thing to do here is to eagerly resolve the type ahead of its first usage. This will allow me to check
    // for impossible type definitions (since otherwise they obviously won't be used - since...it's impossible lol). I
    // don't want to wait until the user tries and fails before I warn about the impossible type definition.
    this.resolvedType = scopedHeap.getValidatedIdentifierType(this.alias);
    if (this.resolvedType == null) {
      this.resolvedType = TypeProvider.Util.getTypeByName(this.alias, true).resolveType(scopedHeap);
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Just so that other statements can still access this type upon re-entering this scope, redefine the type if
    // necessary (this is only the case for an alias defined within a function definition statement).
    if (!scopedHeap.isIdentifierDeclared(this.alias)) {
      scopedHeap.putIdentifierValue(this.alias, this.resolvedType);
      scopedHeap.markIdentifierUsed(this.alias);
      scopedHeap.markIdentifierAsTypeDefinition(this.alias);
    }

    // There's no code to generate for this statement, this is merely a statement giving Claro more
    // information to work with at compile-time.
    return GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Just so that other statements can still access this type upon re-entering this scope, redefine the type if
    // necessary (this is only the case for an alias defined within a function definition statement).
    if (!scopedHeap.isIdentifierDeclared(this.alias)) {
      scopedHeap.putIdentifierValue(this.alias, this.resolvedType);
      scopedHeap.markIdentifierUsed(this.alias);
      scopedHeap.markIdentifierAsTypeDefinition(this.alias);
    }
    // There's nothing to do for this statement, this is merely a statement giving Claro more
    // information to work with.
    return null;
  }
}
