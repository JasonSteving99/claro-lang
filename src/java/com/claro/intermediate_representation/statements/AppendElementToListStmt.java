package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.builtins_impls.collections.ClaroList;
import com.google.common.collect.ImmutableList;

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
      Type expectedListElementType = this.toAppendExpr.getValidatedExprType(scopedHeap);
      if (expectedListElementType.baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
        // In this case there's not really a good error message to give so just complain about the base type.
        this.listExpr.logTypeError(new ClaroTypeException(actualListExprType, BaseType.LIST));
      }
      return;
    }
    // If it's actually a list I need to make sure that the type being added to the list is the correct type.
    this.toAppendExpr.assertExpectedExprType(scopedHeap, ((Types.ListType) actualListExprType).getElementType());
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
