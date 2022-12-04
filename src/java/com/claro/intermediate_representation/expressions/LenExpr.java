package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.function.Supplier;

public class LenExpr extends Expr {
  private static final ImmutableSet<BaseType> SUPPORTED_EXPR_BASE_TYPES =
      ImmutableSet.of(
          BaseType.LIST,
          BaseType.STRING,
          BaseType.MAP
      );

  public LenExpr(Expr e, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(e), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // TODO(steving) Add support for len of Array/Tuple as well.
    ((Expr) this.getChildren().get(0)).assertSupportedExprBaseType(scopedHeap, SUPPORTED_EXPR_BASE_TYPES);

    return Types.INTEGER;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res =
        ((Expr) this.getChildren().get(0)).generateJavaSourceOutput(scopedHeap);
    res.javaSourceBody().append(".length()");
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((ArrayList) this.getChildren().get(0).generateInterpretedOutput(scopedHeap)).size();
  }
}
