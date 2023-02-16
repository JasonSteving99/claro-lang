package com.claro.intermediate_representation.expressions.bool;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.function.Supplier;

public class EqualsBoolExpr extends BoolExpr {

  public EqualsBoolExpr(Expr lhs, Expr rhs, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(lhs, rhs), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  protected ImmutableSet<Type> getSupportedOperandTypes() {
    // The (ugly) contract here is that returning this empty set means that we'll accept any type, so long as both
    // operands are of the same type.
    return ImmutableSet.of();
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Type lhsType = ((Expr) this.getChildren().get(0)).getValidatedExprType(scopedHeap);
    Expr rhs = (Expr) this.getChildren().get(1);
    if (lhsType.baseType().equals(BaseType.ONEOF)) {
      // By definition, we'll allow any of the type variants supported by this particular oneof instance.
      rhs.assertSupportedExprType(
          scopedHeap,
          ImmutableSet.<Type>builder().addAll(((Types.OneofType) lhsType).getVariantTypes())
              .add(lhsType)
              .build()
      );
      return Types.BOOLEAN;
    } else {
      Type rhsType = rhs.getValidatedExprType(scopedHeap);
      if (rhsType.baseType().equals(BaseType.ONEOF)) {
        ((Expr) this.getChildren().get(0)).assertSupportedExprType(
            scopedHeap,
            ImmutableSet.<Type>builder().addAll(((Types.OneofType) rhsType).getVariantTypes())
                .add(rhsType)
                .build()
        );
        return Types.BOOLEAN;
      }
    }
    return super.getValidatedExprType(scopedHeap);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource exprGenJavaSource0 = this.getChildren().get(0).generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource exprGenJavaSource1 = this.getChildren().get(1).generateJavaSourceOutput(scopedHeap);

    GeneratedJavaSource eqExprGenJavaSource =
        GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder(
                String.format(
                    "%s.equals(%s)",
                    exprGenJavaSource0.javaSourceBody().toString(),
                    exprGenJavaSource1.javaSourceBody().toString()
                )));

    // We've already used the javaSourceBody's, we're safe to clear them.
    exprGenJavaSource0.javaSourceBody().setLength(0);
    exprGenJavaSource1.javaSourceBody().setLength(0);
    return eqExprGenJavaSource.createMerged(exprGenJavaSource0).createMerged(exprGenJavaSource1);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return this.getChildren().get(0).generateInterpretedOutput(scopedHeap)
        .equals(
            this.getChildren().get(1).generateInterpretedOutput(scopedHeap));
  }
}
