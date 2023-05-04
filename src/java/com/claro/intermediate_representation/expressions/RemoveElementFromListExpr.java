package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.*;
import com.claro.intermediate_representation.types.impls.builtins_impls.collections.ClaroList;
import com.google.common.collect.ImmutableList;

import java.util.function.Supplier;

public class RemoveElementFromListExpr extends Expr {
  private final Expr listExpr;
  private final Expr indexExpr;

  public RemoveElementFromListExpr(Expr listExpr, Expr indexExpr, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.listExpr = listExpr;
    this.indexExpr = indexExpr;
  }


  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // First of all I need to ensure that the listExpr is actually a List.
    Type actualListExprType = this.listExpr.getValidatedExprType(scopedHeap);
    if (!actualListExprType.baseType().equals(BaseType.LIST)) {
      // Make sure that this mismatch is logged on the offending Expr that was supposed to be a List.
      // In this case there's not really a good error message to give so just complain about the base type.
      this.listExpr.logTypeError(new ClaroTypeException(actualListExprType, BaseType.LIST));
      this.indexExpr.assertExpectedExprType(scopedHeap, Types.INTEGER);
      return Types.UNKNOWABLE;
    }
    if (!((SupportsMutableVariant<?>) actualListExprType).isMutable()) {
      // Make sure that this collection *actually* supports mutation.
      listExpr.logTypeError(
          ClaroTypeException.forIllegalMutationAttemptOnImmutableValue(
              actualListExprType, ((SupportsMutableVariant<?>) actualListExprType).toShallowlyMutableVariant()));
      // The entire premise of this assignment statement is invalid. However, just in case we need to mark things used,
      // let's run validation on the subscript expr and RHS...not perfect but helpful.
    }

    this.indexExpr.assertExpectedExprType(scopedHeap, Types.INTEGER);
    return ((Types.ListType) actualListExprType).getElementType();
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = this.listExpr.generateJavaSourceOutput(scopedHeap);
    res.javaSourceBody().append(".remove(");
    res = res.createMerged(this.indexExpr.generateJavaSourceOutput(scopedHeap));
    res.javaSourceBody().append(".intValue())");
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((ClaroList) this.listExpr.generateInterpretedOutput(scopedHeap))
        .remove(((Integer) this.indexExpr.generateInterpretedOutput(scopedHeap)).intValue());
  }
}
