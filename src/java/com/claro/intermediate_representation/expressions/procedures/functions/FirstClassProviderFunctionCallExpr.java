package com.claro.intermediate_representation.expressions.procedures.functions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.ProcedureDefinitionStmt;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * This is a derivative of the ProviderFunctionCallExpr that exists solely to handle calls to functions that are certainly not
 * generic because they're being passed around as first-class values. This is used by the grammar rule for `expr(args...)`
 * rather than `identifier(args...)` as the latter may be referencing a generic function for which we need to trigger
 * monomorphization etc. So, surprisingly, this represents a much "simpler" node even though the child may be any
 * arbitrary Expr.
 */
public class FirstClassProviderFunctionCallExpr extends Expr {
  private final Expr providerExpr;
  private Optional<Type> expectedExprType = Optional.empty();

  public FirstClassProviderFunctionCallExpr(Expr functionExpr, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.providerExpr = functionExpr;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    this.expectedExprType = Optional.of(expectedExprType);
    super.assertExpectedExprType(scopedHeap, expectedExprType);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Type referencedIdentifierType = this.providerExpr.getValidatedExprType(scopedHeap);
    if (!ImmutableSet.of(BaseType.FUNCTION, BaseType.PROVIDER_FUNCTION, BaseType.CONSUMER_FUNCTION)
        .contains(referencedIdentifierType.baseType())) {
      ImmutableList.Builder<Type> validatedArgTypes = ImmutableList.builder();
      this.providerExpr.logTypeError(
          new ClaroTypeException(
              referencedIdentifierType,
              Types.ProcedureType.ProviderType.typeLiteralForReturnType(
                  this.expectedExprType.orElse(Types.$GenericTypeParam.forTypeParamName("$Out")),
                  /*explicitlyAnnotatedBlocking=*/false
              )
          ));
    }
    if (!((Types.ProcedureType) referencedIdentifierType).hasReturnValue()) {
      this.providerExpr.logTypeError(
          ClaroTypeException.forIllegalConsumerCallUsedAsExpression(referencedIdentifierType));
      return Types.UNKNOWABLE;
    }
    if (((Types.ProcedureType) referencedIdentifierType).hasArgs()) {
      this.providerExpr.logTypeError(
          ClaroTypeException.forFunctionCallWithWrongNumberOfArgs(
              ((Types.ProcedureType) referencedIdentifierType).getArgTypes().size(), referencedIdentifierType, 0));
      return Optional.ofNullable(((Types.ProcedureType) referencedIdentifierType).getReturnType())
          .orElse(Types.UNKNOWABLE);
    }

    // If this happens to be a call to a blocking procedure within another procedure definition, we need to
    // propagate the blocking annotation. In service of Claro's goal to provide "Fearless Concurrency" through Graph
    // Procedures, any procedure that can reach a blocking operation is marked as blocking so that we can prevent its
    // usage from Graph Functions.
    if (InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.isPresent()) {
      if (((Types.ProcedureType) referencedIdentifierType).getAnnotatedBlocking()) {
        ((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
            .get())
            .resolvedProcedureType.getIsBlocking().set(true);
      }
    }

    return ((Types.ProcedureType.ProviderType) referencedIdentifierType).getReturnType();
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource functionCallJavaSourceBody = this.providerExpr.generateJavaSourceOutput(scopedHeap);
    functionCallJavaSourceBody.javaSourceBody()
        .append(".apply()");
    return functionCallJavaSourceBody;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((Types.ProcedureType.ProcedureWrapper) this.providerExpr.generateInterpretedOutput(scopedHeap))
        .apply(scopedHeap);
  }
}
