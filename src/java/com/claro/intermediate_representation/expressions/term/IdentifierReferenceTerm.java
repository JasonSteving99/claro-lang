package com.claro.intermediate_representation.expressions.term;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import java.util.function.Supplier;

public class IdentifierReferenceTerm extends Term {

  public final String identifier;

  public IdentifierReferenceTerm(String identifier, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(currentLine, currentLineNumber, startCol, endCol);
    // Hold onto the relevant data for code-gen later.
    this.identifier = identifier;
  }

  public String getIdentifier() {
    return identifier;
  }

  // We would like to support treating certain referenced identifiers as the asserted type, even if the Expr
  // referenced by this identifier isn't strictly the same type. This will, in particular, allow blocking-generic
  // procedures to be assigned to non-blocking-generic variables as higher order procedures by erasing the
  // blocking-generic type annotation.
  @Override
  public boolean coerceExprToExpectedType(Type expectedExprType, Type actualExprType, ScopedHeap scopedHeap) {
    ImmutableSet<BaseType> allowedBaseTypes =
        ImmutableSet.of(BaseType.FUNCTION, BaseType.CONSUMER_FUNCTION);

    // We're possibly able to coerce blocking-generic procedures into their non-blocking-generic equivalent.
    if (actualExprType.baseType().equals(expectedExprType.baseType())
        && allowedBaseTypes.contains(actualExprType.baseType())
        && ((Types.ProcedureType) actualExprType).getAnnotatedBlockingGenericOverArgs().isPresent()
        // Conceptually, we could allow narrowing blocking-generic args, while staying blocking-generic,
        // but, I don't think this is worth the added complexity at least w/o hearing some user demand.
        && !((Types.ProcedureType) expectedExprType).getAnnotatedBlockingGenericOverArgs().isPresent()) {
      Types.ProcedureType actualProcedureType = (Types.ProcedureType) actualExprType;
      Types.ProcedureType expectedProcedureType = (Types.ProcedureType) expectedExprType;

      Type legalTypeCoercion;
      // For now, we only give the user two options for coercion:
      // blocking-generic -> all-args-blocking
      if (expectedProcedureType.getAnnotatedBlocking()) {
        legalTypeCoercion =
            scopedHeap.getValidatedIdentifierType(
                "$blockingConcreteVariant_"
                // Refer to the name of the procedure as defined by the owning ProcedureDefinitionStmt rather than
                // just the name of the identifier since procedures may be assigned to vars as first class objects.
                + (((Types.ProcedureType) actualExprType).getProcedureDefStmt())
                    .opaqueData_workaroundToAvoidCircularDepsCausedByExprToStmtBuildTargets.get());
      } else {
        // blocking-generic -> non-blocking
        legalTypeCoercion =
            scopedHeap.getValidatedIdentifierType(
                "$nonBlockingConcreteVariant_"
                // Refer to the name of the procedure as defined by the owning ProcedureDefinitionStmt rather than
                // just the name of the identifier since procedures may be assigned to vars as first class objects.
                + (((Types.ProcedureType) actualExprType).getProcedureDefStmt())
                    .opaqueData_workaroundToAvoidCircularDepsCausedByExprToStmtBuildTargets.get());
      }

      return legalTypeCoercion.equals(expectedExprType);
    }

    // False, indicates that coercion was not possible for the given identifier and expected type due to basic type
    // mismatch error. We won't throw exceptions here because superclass will log this basic type mismatch for us.
    return false;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) {
    // Make sure we check this will actually be a valid reference before we allow it.
    Preconditions.checkState(
        scopedHeap.isIdentifierDeclared(this.identifier),
        "No variable <%s> within the current scope!",
        this.identifier
    );
    Preconditions.checkState(
        scopedHeap.isIdentifierInitialized(this.identifier),
        "Variable <%s> may not have been initialized!",
        this.identifier
    );
    scopedHeap.markIdentifierUsed(this.identifier);
    return scopedHeap.getValidatedIdentifierType(this.identifier);
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    scopedHeap.markIdentifierUsed(this.identifier);
    return new StringBuilder(this.identifier);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    scopedHeap.markIdentifierUsed(this.identifier);
    return scopedHeap.getIdentifierValue(this.identifier);
  }
}
