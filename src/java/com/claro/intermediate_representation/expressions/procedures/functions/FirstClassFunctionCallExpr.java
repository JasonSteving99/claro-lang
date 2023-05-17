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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This is a derivative of the FunctionCallExpr that exists solely to handle calls to functions that are certainly not
 * generic because they're being passed around as first-class values. This is used by the grammar rule for `expr(args...)`
 * rather than `identifier(args...)` as the latter may be referencing a generic function for which we need to trigger
 * monomorphization etc. So, surprisingly, this represents a much "simpler" node even though the child may be any
 * arbitrary Expr.
 */
public class FirstClassFunctionCallExpr extends Expr {
  private final Expr functionExpr;
  public final ImmutableList<Expr> argExprs;
  private Optional<Type> expectedExprType = Optional.empty();

  public FirstClassFunctionCallExpr(Expr functionExpr, ImmutableList<Expr> args, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.functionExpr = functionExpr;
    this.argExprs = args;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    this.expectedExprType = Optional.of(expectedExprType);
    super.assertExpectedExprType(scopedHeap, expectedExprType);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Type referencedIdentifierType = this.functionExpr.getValidatedExprType(scopedHeap);
    // Include CONSUMER_FUNCTION just so that later we can throw a more specific error for that case.
    if (!ImmutableSet.of(BaseType.FUNCTION, BaseType.PROVIDER_FUNCTION, BaseType.CONSUMER_FUNCTION)
        .contains(referencedIdentifierType.baseType())) {
      ImmutableList.Builder<Type> validatedArgTypes = ImmutableList.builder();
      for (Expr argExpr : this.argExprs) {
        try {
          validatedArgTypes.add(argExpr.getValidatedExprType(scopedHeap));
        } catch (ClaroTypeException e) {
          validatedArgTypes.add(Types.UNKNOWABLE);
        }
      }
      this.functionExpr.logTypeError(
          new ClaroTypeException(
              referencedIdentifierType,
              Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
                  validatedArgTypes.build(),
                  this.expectedExprType.orElse(Types.$GenericTypeParam.forTypeParamName("$Out")),
                  /*explicitlyAnnotatedBlocking=*/false
              )
          ));
    }
    if (!((Types.ProcedureType) referencedIdentifierType).hasArgs()) {
      this.functionExpr.logTypeError(
          ClaroTypeException.forUnexpectedArgsPassedToProviderCall(referencedIdentifierType, this.argExprs.size()));
      return ((Types.ProcedureType.ProviderType) referencedIdentifierType).getReturnType();
    }
    if (!((Types.ProcedureType) referencedIdentifierType).hasReturnValue()) {
      this.functionExpr.logTypeError(
          ClaroTypeException.forIllegalConsumerCallUsedAsExpression(referencedIdentifierType));
      return Types.UNKNOWABLE;
    }
    ImmutableList<Type> definedArgTypes = ((Types.ProcedureType.FunctionType) referencedIdentifierType).getArgTypes();
    int argsCount = definedArgTypes.size();

    // Make sure that we at least do due diligence and first check that we have the right number of args.
    if (argsCount != this.argExprs.size()) {
      throw ClaroTypeException.forFunctionCallWithWrongNumberOfArgs(
          argsCount,
          referencedIdentifierType,
          this.argExprs.size()
      );
    }

    // Validate that all of the given parameter Exprs are of the correct type.
    for (int i = 0; i < this.argExprs.size(); i++) {
      if (definedArgTypes.get(i).baseType().equals(BaseType.ONEOF)) {
        // Since the arg type itself is a oneof, then we actually really need to accept any variant type.
        this.argExprs.get(i).assertSupportedExprOneofTypeVariant(
            scopedHeap,
            definedArgTypes.get(i),
            ((Types.OneofType) definedArgTypes.get(i)).getVariantTypes()
        );
      } else {
        this.argExprs.get(i).assertExpectedExprType(scopedHeap, definedArgTypes.get(i));
      }
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

    return ((Types.ProcedureType.FunctionType) referencedIdentifierType).getReturnType();
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    AtomicReference<GeneratedJavaSource> exprsGenJavaSource =
        new AtomicReference<>(GeneratedJavaSource.forJavaSourceBody(new StringBuilder()));
    String exprsJavaSourceBodyCodegen =
        this.argExprs
            .stream()
            .map(expr -> {
              GeneratedJavaSource currGenJavaSource = expr.generateJavaSourceOutput(scopedHeap);
              String currJavaSourceBody = currGenJavaSource.javaSourceBody().toString();
              // We've already consumed the javaSourceBody, it's safe to clear it.
              currGenJavaSource.javaSourceBody().setLength(0);
              exprsGenJavaSource.set(exprsGenJavaSource.get().createMerged(currGenJavaSource));
              return currJavaSourceBody;
            })
            .collect(Collectors.joining(", "));
    GeneratedJavaSource functionCallJavaSourceBody = this.functionExpr.generateJavaSourceOutput(scopedHeap);
    functionCallJavaSourceBody.javaSourceBody()
        .append(String.format(".apply(%s)", exprsJavaSourceBodyCodegen));

    // We definitely don't want to be throwing away the static definitions and preambles required for the exprs
    // passed as args to this function call, so ensure that they're correctly collected and passed on here.
    return functionCallJavaSourceBody.createMerged(exprsGenJavaSource.get());
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((Types.ProcedureType.ProcedureWrapper) this.functionExpr.generateInterpretedOutput(scopedHeap))
        .apply(this.argExprs, scopedHeap);
  }
}
