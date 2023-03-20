package com.claro.intermediate_representation.statements.user_defined_type_def_stmts;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.statements.FunctionDefinitionStmt;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.statements.StmtListNode;
import com.claro.intermediate_representation.types.*;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class NewTypeDefStmt extends Stmt implements UserDefinedTypeDefinitionStmt {
  private final String typeName;
  private final TypeProvider baseTypeProvider;
  private Type resolvedType;

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
    scopedHeap.putIdentifierValue(
        this.typeName + "$constructor",
        null,
        (TypeProvider) (scopedHeap1) -> {
          Types.UserDefinedType resolvedUserDefinedType = (Types.UserDefinedType)
              TypeProvider.Util.getTypeByName(this.typeName, /*isTypeDefinition=*/true).resolveType(scopedHeap1);
          return Types.ProcedureType.FunctionType.forArgsAndReturnTypes(
              /*argTypes=*/ImmutableList.of(resolvedUserDefinedType.getWrappedType()),
              /*returnType=*/resolvedUserDefinedType,
              /*directUsedInjectedKeys=*/ImmutableSet.of(),
                           new FunctionDefinitionStmt(
                               this.typeName + "$constructor",
                               BaseType.FUNCTION,
                               ImmutableMap.of("$baseType", TypeProvider.ImmediateTypeProvider.of(resolvedUserDefinedType.getWrappedType())),
                               TypeProvider.Util.getTypeByName(this.typeName, /*isTypeDefinition=*/true),
                               new StmtListNode(new Stmt(ImmutableList.of()) {
                                 @Override
                                 public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
                                   // Intentionall No-Op.
                                 }

                                 @Override
                                 public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
                                   throw new RuntimeException("Internal Compiler Error: This should be unreachable!");
                                 }

                                 @Override
                                 public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
                                   throw new RuntimeException("Internal Compiler Error: This should be unreachable!");
                                 }
                               })
                           ),
              /*explicitlyAnnotatedBlocking=*/false
          );
        }
    );
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
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Just so that other statements can still access this type upon re-entering this scope, redefine the type if
    // necessary (this is only the case for an alias defined within a function definition statement).
    if (!scopedHeap.isIdentifierDeclared(this.typeName)) {
      scopedHeap.putIdentifierValue(this.typeName, this.resolvedType);
      scopedHeap.markIdentifierUsed(this.typeName);
      scopedHeap.markIdentifierAsTypeDefinition(this.typeName);
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
    }
    // There's nothing to do for this statement, this is merely a statement giving Claro more
    // information to work with.
    return null;
  }

}
