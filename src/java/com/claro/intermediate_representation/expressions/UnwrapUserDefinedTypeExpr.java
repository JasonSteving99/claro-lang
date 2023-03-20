package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.user_defined_impls.$UserDefinedType;
import com.google.common.collect.ImmutableList;

import java.util.function.Supplier;

public class UnwrapUserDefinedTypeExpr extends Expr {
  private final Expr expr;

  public UnwrapUserDefinedTypeExpr(Expr expr, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.expr = expr;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Type validatedExprType = this.expr.getValidatedExprType(scopedHeap);
    if (!validatedExprType.baseType().equals(BaseType.USER_DEFINED_TYPE)) {
      if (validatedExprType.baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
        this.expr.logTypeError(ClaroTypeException.forInvalidUnwrapOfGenericType(validatedExprType));
      } else {
        this.expr.logTypeError(ClaroTypeException.forInvalidUnwrapOfBuiltinType(validatedExprType));
      }
      return Types.UNKNOWABLE;
    }
    return ((Types.UserDefinedType) validatedExprType).getWrappedType();
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = expr.generateJavaSourceOutput(scopedHeap);
    res.javaSourceBody().append(".wrappedValue");
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return (($UserDefinedType) expr.generateInterpretedOutput(scopedHeap)).wrappedValue;
  }
}
