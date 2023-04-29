package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class FormatStringExpr extends Expr {
  public final ImmutableList<String> fmtStringParts;
  public final ImmutableList<Expr> fmtExprArgs;

  public FormatStringExpr(ImmutableList<String> fmtStringParts, ImmutableList<Expr> fmtExprArgs, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.fmtStringParts = fmtStringParts;
    this.fmtExprArgs = fmtExprArgs;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // TODO(steving) In the future I'll want to instead defer to some builtin Contract, like Conversion<T, string>.
    // I don't have any particular type constraints on anything formatted. I'll just call .toString() on whatever's
    // passed.
    for (Expr expr : this.fmtExprArgs) {
      expr.getValidatedExprType(scopedHeap);
    }
    return Types.STRING;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder res = new StringBuilder("new StringBuilder()");

    AtomicReference<GeneratedJavaSource> mergedStaticDefinitionsAndPreambleForFmtArgParts =
        new AtomicReference<>(GeneratedJavaSource.forJavaSourceBody(new StringBuilder()));

    Streams.forEachPair(
        this.fmtStringParts.stream(),
        this.fmtExprArgs.stream(),
        (fmtStringPart, fmtArgPart) -> {
          GeneratedJavaSource fmtArgPartGenJavaSource = fmtArgPart.generateJavaSourceOutput(scopedHeap);
          res.append(".append(\"")
              .append(fmtStringPart)
              .append("\").append(")
              .append(fmtArgPartGenJavaSource.javaSourceBody().toString())
              .append(")");
          // We already consumed the javaSourceBody, so we can clear it now.
          fmtArgPartGenJavaSource.javaSourceBody().setLength(0);
          mergedStaticDefinitionsAndPreambleForFmtArgParts.set(
              mergedStaticDefinitionsAndPreambleForFmtArgParts.get().createMerged(fmtArgPartGenJavaSource));
        }
    );

    return GeneratedJavaSource.forJavaSourceBody(
        res.append(".append(\"")
            .append(this.fmtStringParts.get(fmtStringParts.size() - 1))
            .append("\").toString()"))
        .createMerged(mergedStaticDefinitionsAndPreambleForFmtArgParts.get());
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
