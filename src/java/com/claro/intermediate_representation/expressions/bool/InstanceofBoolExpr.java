package com.claro.intermediate_representation.expressions.bool;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.function.Supplier;

public class InstanceofBoolExpr extends BoolExpr {


  private final Expr oneofExpr;
  private final TypeProvider checkedTypeProvider;
  private Type validatedOneofExprType;
  private Type validatedCheckedType;

  public InstanceofBoolExpr(Expr oneofExpr, TypeProvider checkedTypeProvider, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.oneofExpr = oneofExpr;
    this.checkedTypeProvider = checkedTypeProvider;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Checked type has to be a concrete (non-oneof) type.
    Type checkedType = checkedTypeProvider.resolveType(scopedHeap);
    if (checkedType.baseType().equals(BaseType.ONEOF)) {
      // Log and move on nicely.
      this.logTypeError(ClaroTypeException.forIllegalInstanceofCheckAgainstOneofType(checkedType));
      return Types.BOOLEAN;
    }
    this.validatedOneofExprType = this.oneofExpr.getValidatedExprType(scopedHeap);
    if (!this.validatedOneofExprType.baseType().equals(BaseType.ONEOF)) {
      // Log and move on nicely.
      this.logTypeError(ClaroTypeException.forIllegalInstanceofCheckOverNonOneofExpr(this.validatedOneofExprType));
      return Types.BOOLEAN;
    }

    // If we get here, the known oneof matches the checked type, and since the checked type wasn't also a oneof,
    // then that means we're going to do some type narrowing if we're going into a condition body scope and we can
    // narrow a specific value by identifier name (so obviously not through some collection subscript or procedure
    // call).
    if (InternalStaticStateUtil.IfStmt_withinConditionTypeValidation
        && this.oneofExpr instanceof IdentifierReferenceTerm) {
      this.oneofsToBeNarrowed.put(
          ((IdentifierReferenceTerm) this.oneofExpr).identifier,
          checkedType
      );
    }

    this.validatedCheckedType = checkedType;
    return Types.BOOLEAN;
  }

  @Override
  protected ImmutableSet<Type> getSupportedOperandTypes() {
    throw new IllegalStateException("Internal Compiler Error! This should be unreachable.");
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource oneofExprGeneratedJavaSource = this.oneofExpr.generateJavaSourceOutput(scopedHeap);

    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            this.validatedCheckedType instanceof ConcreteType
            ? String.format(
                "%s instanceof %s",
                oneofExprGeneratedJavaSource.javaSourceBody(),
                this.validatedCheckedType.getJavaSourceType()
            ) :
            String.format(
                "ClaroRuntimeUtilities.$instanceof_ClaroTypeImpl(%s, %s)",
                oneofExprGeneratedJavaSource.javaSourceBody(),
                this.validatedCheckedType.getJavaSourceClaroType()
            )
        ));

    // We've already picked up the javaSourceBody, so we don't need it again.
    oneofExprGeneratedJavaSource.javaSourceBody().setLength(0);

    // Need to ensure that we pick up the static definitions from each child expr.
    return res.createMerged(oneofExprGeneratedJavaSource);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    throw new ClaroParserException("Internal Compiler Error: Interpreted backend doesn't support this operation yet.");
  }
}
