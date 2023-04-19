package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.*;
import com.claro.intermediate_representation.types.impls.builtins_impls.collections.ClaroList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class AppendElementToListStmt extends Stmt {
  private final Expr listExpr;
  private final Expr toAppendExpr;

  public AppendElementToListStmt(Expr listExpr, Expr toAppendExpr) {
    super(ImmutableList.of());
    this.listExpr = listExpr;
    this.toAppendExpr = toAppendExpr;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // First of all I need to ensure that the listExpr is actually a List.
    Type actualListExprType = this.listExpr.getValidatedExprType(scopedHeap);
    if (!actualListExprType.baseType().equals(BaseType.LIST)) {
      // Make sure that this mismatch is logged on the offending Expr that was supposed to be a List.
      // In this case there's not really a good error message to give so just complain about the base type.
      this.listExpr.logTypeError(new ClaroTypeException(actualListExprType, BaseType.LIST));
      // We really should be asserting the type, but in order to avoid unused warnings, at least do best effort of
      // actually doing some type validation. Not perfect but better than nothing.
      this.toAppendExpr.getValidatedExprType(scopedHeap);
      return;
    }
    if (!((SupportsMutableVariant<?>) actualListExprType).isMutable()) {
      // Make sure that this collection *actually* supports mutation.
      listExpr.logTypeError(
          ClaroTypeException.forIllegalMutationAttemptOnImmutableValue(
              actualListExprType, ((SupportsMutableVariant<?>) actualListExprType).toShallowlyMutableVariant()));
      // The entire premise of this assignment statement is invalid. However, just in case we need to mark things used,
      // let's run validation on the subscript expr and RHS...not perfect but helpful.
    }
    // If it's actually a mutable list I need to make sure that the type being added to the list is the correct type.
    Type declaredElementType = ((Types.ListType) actualListExprType).getElementType();
    if (declaredElementType.baseType().equals(BaseType.ONEOF)) {
      // Since this is assignment to a oneof type, by definition we'll allow any of the type variants supported
      // by this particular oneof instance.
      this.toAppendExpr.assertSupportedExprType(
          scopedHeap,
          ImmutableSet.<Type>builder().addAll(((Types.OneofType) declaredElementType).getVariantTypes())
              .add(declaredElementType)
              .build()
      );
    } else {
      this.toAppendExpr.assertExpectedExprType(scopedHeap, declaredElementType);
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = this.listExpr.generateJavaSourceOutput(scopedHeap);
    res.javaSourceBody().append(".add(");
    res = res.createMerged(this.toAppendExpr.generateJavaSourceOutput(scopedHeap));
    res.javaSourceBody().append(");\n");
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    ((ClaroList) this.listExpr.generateInterpretedOutput(scopedHeap))
        .add(this.toAppendExpr.generateInterpretedOutput(scopedHeap));
    return null;
  }
}
