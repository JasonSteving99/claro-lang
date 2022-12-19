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
  private final String alias;
  private final TypeProvider aliasedType;

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
    // TypeProvider that will be used for type-resolution in the later phase. Mimicking the StructDefinitionStmt approach.
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
    // Intentionally left unimplemented, there's nothing for us to do here.
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Update AliasStmt to actually function as a constant Singleton Value so that Types can be passed as first class objects in Claro.
    // There's no code to generate for this statement, this is merely a statement giving Claro more
    // information to work with at compile-time.
    return GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Update AliasStmt to actually function as a constant Singleton Value so that Types can be passed as first class objects in Claro.
    // There's nothing to do for this statement, this is merely a statement giving Claro more
    // information to work with.
    return null;
  }
}
