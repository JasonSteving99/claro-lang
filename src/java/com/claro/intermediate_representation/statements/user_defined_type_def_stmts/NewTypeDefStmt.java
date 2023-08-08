package com.claro.intermediate_representation.statements.user_defined_type_def_stmts;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.*;
import com.claro.intermediate_representation.types.*;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class NewTypeDefStmt extends Stmt implements UserDefinedTypeDefinitionStmt {
  private static final String CURR_TYPE_DEF_NAME = "$CURR_TYPE_DEF_NAME";
  public final String typeName;
  private final Optional<String> optionalOriginatingModuleDisambiguator;
  private final TypeProvider wrappedTypeProvider;
  private final ImmutableList<String> parameterizedTypeNames;
  public Type resolvedType;
  private Type resolvedDefaultConstructorType;
  private Stmt constructorFuncDefStmt;
  private String wrappedTypeIdentifier = null;
  private String constructorIdentifier = null;

  public NewTypeDefStmt(String typeName, Optional<String> optionalOriginatingModuleDisambiguator, TypeProvider wrappedTypeProvider) {
    super(ImmutableList.of());
    this.typeName = typeName;
    this.optionalOriginatingModuleDisambiguator = optionalOriginatingModuleDisambiguator;
    this.wrappedTypeProvider = wrappedTypeProvider;
    this.parameterizedTypeNames = ImmutableList.of();
  }

  public NewTypeDefStmt(String typeName, Optional<String> optionalOriginatingModuleDisambiguator, TypeProvider wrappedTypeProvider, ImmutableList<String> parameterizedTypeNames) {
    super(ImmutableList.of());
    this.typeName = typeName;
    this.optionalOriginatingModuleDisambiguator = optionalOriginatingModuleDisambiguator;
    this.wrappedTypeProvider = wrappedTypeProvider;
    this.parameterizedTypeNames = parameterizedTypeNames;
  }

  @Override
  public void registerTypeProvider(ScopedHeap scopedHeap) {
    // Validate that this is not a redeclaration of an identifier.
    Preconditions.checkState(
        !scopedHeap.isIdentifierDeclared(this.typeName),
        String.format("Unexpected redeclaration of %s.", this.typeName)
    );

    // Register the custom type and its corresponding wrapped type *SEPERATELY*! This is the key difference that enables
    // newtype defs to avoid the infinite recursion hell that alias defs run into.
    scopedHeap.putIdentifierValue(
        this.typeName,
        Types.UserDefinedType.forTypeNameAndParameterizedTypes(
            this.typeName,
            this.optionalOriginatingModuleDisambiguator.orElse(""),
            this.parameterizedTypeNames.stream()
                .map(Types.$GenericTypeParam::forTypeParamName).collect(ImmutableList.toImmutableList())
        ),
        null
    );
    scopedHeap.markIdentifierAsTypeDefinition(this.typeName);
    // Just so that the codegen later on has access, let's immediately register the type param names.
    if (!this.parameterizedTypeNames.isEmpty()) {
      Types.UserDefinedType.$typeParamNames.put(
          String.format(
              "%s$%s",
              this.typeName,
              this.optionalOriginatingModuleDisambiguator.orElse("")
          ),
          this.parameterizedTypeNames
      );
    }

    // Register a null type since it's not yet resolved, and then abuse its Object value field temporarily to hold the
    // TypeProvider that will be used for type-resolution in the later phase. Mimicking the AliasStmt approach.
    scopedHeap.putIdentifierValue(
        getWrappedTypeIdentifier(),
        null,
        (TypeProvider) (scopedHeap1) -> {
          // In order to identify and reject impossible recursive type definitions, we need to be able to track whether
          // or not this particular type definition is recursive, which requires knowing the name of the current type. I
          // have to track the previous to reset it after, because it's actually possible, based on the TypeProvider
          // resolution scheme we're following, that one type definition will be interrupted by resolving another type
          // definition midway through.
          Optional<String> originalTypeName =
              scopedHeap1.isIdentifierDeclared(CURR_TYPE_DEF_NAME)
              ? Optional.of((String) scopedHeap1.getIdentifierValue(CURR_TYPE_DEF_NAME))
              : Optional.empty();
          scopedHeap1.putIdentifierValue(
              CURR_TYPE_DEF_NAME,
              null,
              this.typeName
          );

          // In case this type has parameterized types then they'll need to be defined here. Parameterized type names
          // can be literally anything so create a new temporary scope.
          if (!this.parameterizedTypeNames.isEmpty()) {
            scopedHeap1.enterNewScope();
            this.parameterizedTypeNames.forEach(
                n -> {
                  scopedHeap1.putIdentifierValueAllowingHiding(n, Types.$GenericTypeParam.forTypeParamName(n), null);
                  scopedHeap1.markIdentifierAsTypeDefinition(n);
                });
          }

          // Do actual wrapped type resolution.
          Type res = this.wrappedTypeProvider.resolveType(scopedHeap1);

          // Cleanup after parameterized types.
          if (!this.parameterizedTypeNames.isEmpty()) {
            scopedHeap1.exitCurrScope();
          }

          if (scopedHeap1.isIdentifierDeclared("$POTENTIAL_IMPOSSIBLE_SELF_REFERENCING_TYPE_FOUND")) {
            throw new RuntimeException(ClaroTypeException.forImpossibleRecursiveAliasTypeDefinition(this.typeName));
          }

          // Reset initial state.
          if (originalTypeName.isPresent()) {
            scopedHeap1.updateIdentifierValue(CURR_TYPE_DEF_NAME, originalTypeName.get());
          } else {
            scopedHeap1.deleteIdentifierValue(CURR_TYPE_DEF_NAME);
          }

          return res;
        }
    );
    scopedHeap.markIdentifierAsTypeDefinition(getWrappedTypeIdentifier());
  }

  public void registerConstructorTypeProvider(ScopedHeap scopedHeap) {
    StmtListNode syntheticConstructorProcStmtList =
        new StmtListNode(
            new ReturnStmt(
                new Expr(ImmutableList.of(), () -> "", -1, -1, -1) {
                  @Override
                  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
                    scopedHeap.markIdentifierUsed("$baseType");
                    return TypeProvider.Util.getTypeByName(getWrappedTypeIdentifier(), true)
                        .resolveType(scopedHeap);
                  }

                  @Override
                  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
                    throw new RuntimeException("Internal Compiler Error: This should be unreachable!");
                  }

                  @Override
                  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
                    throw new RuntimeException("Internal Compiler Error: This should be unreachable!");
                  }
                },
                new AtomicReference<>(
                    (scopedHeap1) ->
                        TypeProvider.Util.getTypeByName(getWrappedTypeIdentifier(), true).resolveType(scopedHeap1))
            ));

    if (this.parameterizedTypeNames.isEmpty()) {
      this.constructorFuncDefStmt = new FunctionDefinitionStmt(
          getConstructorIdentifier(),
          BaseType.FUNCTION,
          ImmutableMap.of(
              "$baseType",
              (scopedHeap1) -> {
                Type wrappedType =
                    TypeProvider.Util.getTypeByName(getWrappedTypeIdentifier(), true).resolveType(scopedHeap1);
                Types.UserDefinedType.$resolvedWrappedTypes.put(
                    String.format(
                        "%s$%s",
                        this.typeName,
                        this.optionalOriginatingModuleDisambiguator.orElse("")
                    ),
                    wrappedType
                );
                return wrappedType;
              }
          ),
          TypeProvider.Util.getTypeByName(this.typeName, /*isTypeDefinition=*/true),
          syntheticConstructorProcStmtList
      );
      ((FunctionDefinitionStmt) this.constructorFuncDefStmt).registerProcedureTypeProvider(scopedHeap);
    } else {
      this.constructorFuncDefStmt = new GenericFunctionDefinitionStmt(
          getConstructorIdentifier(),
          ImmutableListMultimap.of(),
          this.parameterizedTypeNames,
          ImmutableMap.of(
              "$baseType",
              (scopedHeap1) -> {
                String wrappedTypeIdentifier = getWrappedTypeIdentifier();
                Type wrappedType =
                    TypeProvider.Util.getTypeByName(wrappedTypeIdentifier, true).resolveType(scopedHeap1);
                Types.UserDefinedType.$resolvedWrappedTypes.put(
                    String.format(
                        "%s$%s",
                        this.typeName,
                        this.optionalOriginatingModuleDisambiguator.orElse("")
                    ),
                    wrappedType
                );
                return wrappedType;
              }
          ),
          /*optionalInjectedKeysTypes=*/Optional.empty(),
          TypeProvider.Util.getTypeByName(this.typeName, /*isTypeDefinition=*/true),
          syntheticConstructorProcStmtList,
          /*explicitlyAnnotatedBlocking=*/false,
          /*optionalGenericBlockingOnArgs=*/Optional.empty()
      );
      try {
        ((GenericFunctionDefinitionStmt) this.constructorFuncDefStmt).registerGenericProcedureTypeProvider(scopedHeap);
      } catch (
          ClaroTypeException e) { // I hate Java's checked Exceptions... they lead you to make horrible design decisions.
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // In order to allow users to define aliases within arbitrary scopes, I want to simply check whether the type is
    // already registered, it may not be in the case that this is nested in some function definition and the type
    // registration pass didn't notice this alias def.
    if (!scopedHeap.isIdentifierDeclared(this.typeName)) {
      registerTypeProvider(scopedHeap);
    }

    // The only thing to do here is to eagerly resolve the type ahead of its first usage. This will allow me to check
    // for impossible type definitions (since otherwise they obviously won't be used - since...it's impossible lol). I
    // don't want to wait until the user tries and fails before I warn about the impossible type definition.
    this.resolvedType = scopedHeap.getValidatedIdentifierType(this.typeName);
    if (this.resolvedType == null) {
      this.resolvedType = TypeProvider.Util.getTypeByName(this.typeName, true).resolveType(scopedHeap);
    }

    // Do type assertion on this synthetic constructor function so that the type can be
    this.constructorFuncDefStmt.assertExpectedExprTypes(scopedHeap);
    this.resolvedDefaultConstructorType = scopedHeap.getValidatedIdentifierType(getConstructorIdentifier());
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Just so that other statements can still access this type upon re-entering this scope, redefine the type if
    // necessary (this is only the case for an alias defined within a function definition statement).
    if (!scopedHeap.isIdentifierDeclared(this.typeName)) {
      scopedHeap.putIdentifierValue(this.typeName, this.resolvedType);
      scopedHeap.markIdentifierUsed(this.typeName);
      scopedHeap.markIdentifierAsTypeDefinition(this.typeName);

      String constructorIdentifier = getConstructorIdentifier();
      scopedHeap.putIdentifierValue(constructorIdentifier, this.resolvedDefaultConstructorType);
      scopedHeap.markIdentifierUsed(constructorIdentifier);
    }

    // There's no code to generate for this statement, this is merely a statement giving Claro more
    // information to work with at compile-time.
    return GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Just so that other statements can still access this type upon re-entering this scope, redefine the type if
    // necessary (this is only the case for an alias defined within a function definition statement).
    if (!scopedHeap.isIdentifierDeclared(this.typeName)) {
      scopedHeap.putIdentifierValue(this.typeName, this.resolvedType);
      scopedHeap.markIdentifierUsed(this.typeName);
      scopedHeap.markIdentifierAsTypeDefinition(this.typeName);

      String constructorIdentifier = getConstructorIdentifier();
      scopedHeap.putIdentifierValue(constructorIdentifier, this.resolvedType);
      scopedHeap.markIdentifierUsed(constructorIdentifier);
      scopedHeap.markIdentifierAsTypeDefinition(constructorIdentifier);
    }
    // There's nothing to do for this statement, this is merely a statement giving Claro more
    // information to work with.
    return null;
  }

  public String getWrappedTypeIdentifier() {
    if (this.wrappedTypeIdentifier == null) {
      this.wrappedTypeIdentifier = String.format(
          "%s$%s$wrappedType",
          this.typeName,
          this.optionalOriginatingModuleDisambiguator.orElse("")
      );
    }
    return this.wrappedTypeIdentifier;
  }

  public String getConstructorIdentifier() {
    if (this.constructorIdentifier == null) {
      this.constructorIdentifier = String.format("%s$constructor", this.typeName);
    }
    return this.constructorIdentifier;
  }

}
