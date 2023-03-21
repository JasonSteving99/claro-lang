package com.claro.intermediate_representation.statements.user_defined_type_def_stmts;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.FunctionDefinitionStmt;
import com.claro.intermediate_representation.statements.ReturnStmt;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.statements.StmtListNode;
import com.claro.intermediate_representation.types.*;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.concurrent.atomic.AtomicReference;

public class NewTypeDefStmt extends Stmt implements UserDefinedTypeDefinitionStmt {
  private final String typeName;
  private final TypeProvider baseTypeProvider;
  private Type resolvedType;
  private Type resolvedDefaultConstructorType;
  private FunctionDefinitionStmt constructorFuncDefStmt;

  public NewTypeDefStmt(String typeName, TypeProvider baseTypeProvider) {
    super(ImmutableList.of());
    this.typeName = typeName;
    this.baseTypeProvider = baseTypeProvider;
  }

  @Override
  public void registerTypeProvider(ScopedHeap scopedHeap) {
    // Validate that this is not a redeclaration of an identifier.
    Preconditions.checkState(
        !scopedHeap.isIdentifierDeclared(this.typeName),
        String.format("Unexpected redeclaration of %s.", this.typeName)
    );

    // Register a null type since it's not yet resolved, and then abuse its Object value field temporarily to hold the
    // TypeProvider that will be used for type-resolution in the later phase. Mimicking the AliasStmt approach.
    scopedHeap.putIdentifierValue(
        this.typeName,
        null,
        // TODO(steving) Come back and support recursive type defs as well as generic type defs.
        (TypeProvider) (scopedHeap1) ->
            Types.UserDefinedType.forTypeNameAndWrappedType(
                this.typeName, this.baseTypeProvider.resolveType(scopedHeap1))
    );
    scopedHeap.markIdentifierAsTypeDefinition(this.typeName);
  }

  public void registerConstructorTypeProvider(ScopedHeap scopedHeap) {
    this.constructorFuncDefStmt = new FunctionDefinitionStmt(
        this.typeName + "$constructor",
        BaseType.FUNCTION,
        ImmutableMap.of(
            "$baseType",
            (TypeProvider) (scopedHeap1) ->
                ((Types.UserDefinedType) TypeProvider.Util.getTypeByName(this.typeName, true)
                    .resolveType(scopedHeap1))
                    .getWrappedType()
        ),
        TypeProvider.Util.getTypeByName(this.typeName, /*isTypeDefinition=*/true),
        new StmtListNode(
            new ReturnStmt(
                new Expr(ImmutableList.of(), () -> "", -1, -1, -1) {
                  @Override
                  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
                    scopedHeap.markIdentifierUsed("$baseType");
                    return ((Types.UserDefinedType) TypeProvider.Util
                        .getTypeByName(NewTypeDefStmt.this.typeName, true)
                        .resolveType(scopedHeap))
                        .getWrappedType();
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
                    (TypeProvider) (scopedHeap1) -> ((Types.UserDefinedType) TypeProvider.Util
                        .getTypeByName(NewTypeDefStmt.this.typeName, true)
                        .resolveType(scopedHeap1))
                        .getWrappedType())
            ))
    );
    this.constructorFuncDefStmt.registerProcedureTypeProvider(scopedHeap);
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
    this.resolvedDefaultConstructorType = scopedHeap.getValidatedIdentifierType(this.typeName + "$constructor");
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Just so that other statements can still access this type upon re-entering this scope, redefine the type if
    // necessary (this is only the case for an alias defined within a function definition statement).
    if (!scopedHeap.isIdentifierDeclared(this.typeName)) {
      scopedHeap.putIdentifierValue(this.typeName, this.resolvedType);
      scopedHeap.markIdentifierUsed(this.typeName);
      scopedHeap.markIdentifierAsTypeDefinition(this.typeName);

      scopedHeap.putIdentifierValue(this.typeName + "$constructor", this.resolvedDefaultConstructorType);
      scopedHeap.markIdentifierUsed(this.typeName + "$constructor");
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

      scopedHeap.putIdentifierValue(this.typeName + "$constructor", this.resolvedType);
      scopedHeap.markIdentifierUsed(this.typeName + "$constructor");
      scopedHeap.markIdentifierAsTypeDefinition(this.typeName + "$constructor");
    }
    // There's nothing to do for this statement, this is merely a statement giving Claro more
    // information to work with.
    return null;
  }

}
