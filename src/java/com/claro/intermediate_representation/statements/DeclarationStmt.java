package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Optional;

public class DeclarationStmt extends Stmt {

  private final String IDENTIFIER;

  // Only oneof these should be set.
  private final Optional<TypeProvider> optionalIdentifierDeclaredTypeProvider;
  private final boolean errorProp;
  private Optional<AutomaticErrorPropagationStmt> optionalAutomaticErrorPropagationStmt = Optional.empty();
  private Type identifierValidatedInferredType;

  // Determines whether this variable declaration should allow variable hiding or not. This is not always desirable,
  // so it must be explicitly set if this is desirable in this case.
  private final boolean allowVariableHiding;
  // Determine whether this is a blocking declaration statement, in which case we'll alter the type checking to expect
  // a future and generate some additional Future::get call to unwrap the future.
  private final boolean blocking;

  // Constructor for var initialization requesting type inference.
  public DeclarationStmt(String identifier, Expr e) {
    this(identifier, e, false);
  }

  public DeclarationStmt(String identifier, Expr e, boolean blocking) {
    this(identifier, e, blocking, /*errorProp=*/false);
  }

  public DeclarationStmt(String identifier, Expr e, boolean blocking, boolean errorProp) {
    super(ImmutableList.of(e));
    this.IDENTIFIER = identifier;
    this.optionalIdentifierDeclaredTypeProvider = Optional.empty();
    this.allowVariableHiding = false;
    this.blocking = blocking;
    this.errorProp = errorProp;
  }

  // Allow typed declarations with initialization.
  public DeclarationStmt(String identifier, TypeProvider declaredTypeProvider, Expr e) {
    this(identifier, declaredTypeProvider, e, false, false);
  }

  public DeclarationStmt(String identifier, TypeProvider declaredTypeProvider, Expr e, boolean allowVariableHiding) {
    this(identifier, declaredTypeProvider, e, allowVariableHiding, false);
  }

  // Allow typed declarations with initialization that can hide variables in outer scopes.
  public DeclarationStmt(
      String identifier, TypeProvider declaredTypeProvider, Expr e, boolean allowVariableHiding, boolean blocking) {
    super(ImmutableList.of(e));
    this.IDENTIFIER = identifier;
    this.optionalIdentifierDeclaredTypeProvider = Optional.of(declaredTypeProvider);
    this.allowVariableHiding = allowVariableHiding;
    this.blocking = blocking;
    this.errorProp = false;
  }

  public DeclarationStmt(
      String identifier, TypeProvider declaredTypeProvider, Expr e, boolean allowVariableHiding, boolean blocking, boolean errorProp) {
    super(ImmutableList.of(e));
    this.IDENTIFIER = identifier;
    this.optionalIdentifierDeclaredTypeProvider = Optional.of(declaredTypeProvider);
    this.allowVariableHiding = allowVariableHiding;
    this.blocking = blocking;
    this.errorProp = errorProp;
  }

  // Allow typed declarations without initialization.
  public DeclarationStmt(String identifier, TypeProvider declaredTypeProvider) {
    super(ImmutableList.of());
    this.IDENTIFIER = identifier;
    this.optionalIdentifierDeclaredTypeProvider = Optional.of(declaredTypeProvider);
    this.allowVariableHiding = false;
    this.blocking = false;
    this.errorProp = false;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Validate that this is not a redeclaration of an identifier.
    Preconditions.checkState(
        !scopedHeap.isIdentifierDeclared(this.IDENTIFIER),
        String.format("Unexpected redeclaration of identifier <%s>.", this.IDENTIFIER)
    );

    // Determine which type this identifier was declared as, validating initializer Expr as necessary.
    if (optionalIdentifierDeclaredTypeProvider.isPresent()) {
      Type declaredType = optionalIdentifierDeclaredTypeProvider.get().resolveType(scopedHeap);
      if (!this.getChildren().isEmpty()) {
        if (this.errorProp) {
          // Actually will just defer error propagation handling to AutomaticErrorPropagationStmt.java.
          this.optionalAutomaticErrorPropagationStmt =
              Optional.of(
                  new AutomaticErrorPropagationStmt(Optional.of(declaredType), (Expr) this.getChildren().get(0))
              );
          declaredType = this.optionalAutomaticErrorPropagationStmt.get().getValidatedExprType(scopedHeap);
        } else if (declaredType.baseType().equals(BaseType.ONEOF)) {
          // Since this is assignment to a oneof type, by definition we'll allow any of the type variants supported
          // by this particular oneof instance.
          ((Expr) this.getChildren().get(0)).assertSupportedExprType(
              scopedHeap,
              ImmutableSet.<Type>builder().addAll(((Types.OneofType) declaredType).getVariantTypes())
                  .add(blocking ? Types.FutureType.wrapping(declaredType) : declaredType)
                  .build()
          );
        } else {
          ((Expr) this.getChildren().get(0)).assertExpectedExprType(
              scopedHeap,
              blocking ? Types.FutureType.wrapping(declaredType) : declaredType
          );
        }
        scopedHeap.initializeIdentifier(this.IDENTIFIER);
      }
      scopedHeap.observeIdentifier(this.IDENTIFIER, declaredType);
    } else {
      if (this.errorProp) {
        // Actually will just defer error propagation handling to AutomaticErrorPropagationStmt.java.
        this.optionalAutomaticErrorPropagationStmt =
            Optional.of(
                new AutomaticErrorPropagationStmt(Optional.empty(), (Expr) this.getChildren().get(0))
            );
        this.identifierValidatedInferredType =
            this.optionalAutomaticErrorPropagationStmt.get().getValidatedExprType(scopedHeap);
        scopedHeap.observeIdentifier(this.IDENTIFIER, identifierValidatedInferredType);
        scopedHeap.initializeIdentifier(this.IDENTIFIER);
        return;
      }
      try {
        // Infer the identifier's type only the first time it's assigned to.
        this.identifierValidatedInferredType = ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap);

        // If this is a blocking declaration stmt then we need to ensure that we're unwrapping a future.
        if (blocking) {
          // In service of Claro's goal to provide "Fearless Concurrency" through Graph Functions, any procedure that can
          // reach a blocking operation is marked as blocking so that we can prevent its usage from Graph Functions.
          InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
              .ifPresent(
                  procedureDefinitionStmt ->
                      ((ProcedureDefinitionStmt) procedureDefinitionStmt)
                          .resolvedProcedureType.getIsBlocking().set(true));

          // Let's defer Expr's assertions since that will automatically handle logging.
          ((Expr) this.getChildren().get(0)).assertExpectedBaseType(scopedHeap, BaseType.FUTURE);
          // Unwrap the type since we'll block to unwrap the value.
          this.identifierValidatedInferredType =
              this.identifierValidatedInferredType.parameterizedTypeArgs().get("$value");
        }

        scopedHeap.observeIdentifier(this.IDENTIFIER, identifierValidatedInferredType);
        scopedHeap.initializeIdentifier(this.IDENTIFIER);
      } catch (ClaroTypeException e) {
        // If there was some type issue, then let's attribute the error to the expression and move on with typechecking.
        ((Expr) this.getChildren().get(0)).logTypeError(e);
      }
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder res = new StringBuilder();

    Type identifierValidatedType =
        optionalIdentifierDeclaredTypeProvider.orElse((unused) -> identifierValidatedInferredType)
            .resolveType(scopedHeap);

    // First time we're seeing the variable, so declare it.
    res.append(String.format("%s %s", identifierValidatedType.getJavaSourceType(), this.IDENTIFIER));
    scopedHeap.putIdentifierValue(this.IDENTIFIER, identifierValidatedType);

    GeneratedJavaSource exprGeneratedJavaSource = GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
    // Maybe mark the identifier initialized.
    if (!this.getChildren().isEmpty()) {
      // The identifier is unconditionally initialized because it's happening on the same line as its declaration. No
      // need to worry about other code branches where the identifier may not have been initialized yet.
      scopedHeap.initializeIdentifier(this.IDENTIFIER);

      if (this.errorProp) {
        // Actually defer the codegen to the AutomaticErrorPropagationStmt if we're doing error propagation.
        exprGeneratedJavaSource =
            this.optionalAutomaticErrorPropagationStmt.get().generateJavaSourceOutput(scopedHeap);
      } else {
        exprGeneratedJavaSource = this.getChildren().get(0).generateJavaSourceOutput(scopedHeap);
      }
      res.append(
          String.format(
              " = %s%s",
              exprGeneratedJavaSource.javaSourceBody().toString(),
              blocking ? ".get()" : ""
          ));
      // We already consumed the javaSourceBody so we can clear it out.
      exprGeneratedJavaSource.javaSourceBody().setLength(0);
    }
    res.append(";\n");

    return GeneratedJavaSource.forJavaSourceBody(res).createMerged(exprGeneratedJavaSource);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    if (!this.errorProp) {
      Type identifierValidatedType =
          optionalIdentifierDeclaredTypeProvider.orElse((unused) -> identifierValidatedInferredType)
              .resolveType(scopedHeap);

      // Put the declared variable directly in the heap, with its computed value if initialized.
      if (this.getChildren().isEmpty()) {
        if (allowVariableHiding) {
          scopedHeap.putIdentifierValueAllowingHiding(this.IDENTIFIER, identifierValidatedType, null);
        } else {
          scopedHeap.putIdentifierValue(this.IDENTIFIER, identifierValidatedType);
        }
      } else {
        if (allowVariableHiding) {
          scopedHeap.putIdentifierValueAllowingHiding(
              this.IDENTIFIER, identifierValidatedType, this.getChildren()
                  .get(0)
                  .generateInterpretedOutput(scopedHeap));
        } else {
          scopedHeap.putIdentifierValue(
              this.IDENTIFIER, identifierValidatedType, this.getChildren()
                  .get(0)
                  .generateInterpretedOutput(scopedHeap));
        }
        scopedHeap.initializeIdentifier(this.IDENTIFIER);
      }
      return null;
    } else {
      // TODO(steving) Will need to support early returns here once I get back to supporting the interpreted backend.
      throw new RuntimeException("Internal Compiler Error: Automatic Error Propagating declaration stmts are not yet supported in the interpreted backend!");
    }
  }
}
