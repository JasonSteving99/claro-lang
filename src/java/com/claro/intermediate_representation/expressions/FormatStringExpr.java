package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;

public class FormatStringExpr extends Expr {
  private final ImmutableList<String> fmtStringParts;
  private final ImmutableList<Expr> fmtExprArgs;

  public FormatStringExpr(ImmutableList<String> fmtStringParts, ImmutableList<Expr> fmtExprArgs) {
    super(ImmutableList.of());
    this.fmtStringParts = fmtStringParts;
    this.fmtExprArgs = fmtExprArgs;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Go through all the fmtExprArgs and assert that they're all String type Exprs.
    for (Expr expr : this.fmtExprArgs) {
      expr.assertExpectedExprType(scopedHeap, Types.STRING);
    }

    return Types.STRING;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    StringBuilder res = new StringBuilder("new StringBuilder()");

    Streams.forEachPair(
        this.fmtStringParts.stream(),
        this.fmtExprArgs.stream(),
        (fmtStringPart, fmtArgPart) ->
            res.append(".append(\"")
                .append(fmtStringPart)
                .append("\").append(")
                .append(fmtArgPart.generateJavaSourceBodyOutput(scopedHeap))
                .append(")")
    );

    return res
        .append(".append(\"")
        .append(this.fmtStringParts.get(fmtStringParts.size() - 1))
        .append("\").toString()");
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    StringBuilder res = new StringBuilder();

    Streams.forEachPair(
        this.fmtStringParts.stream(),
        this.fmtExprArgs.stream(),
        (fmtStringPart, fmtArgPart) ->
            res.append(fmtStringPart)
                .append(fmtArgPart.generateInterpretedOutput(scopedHeap))
    );

    return res
        .append(this.fmtStringParts.get(fmtStringParts.size() - 1))
        .toString();
  }
}
