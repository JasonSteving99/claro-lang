package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.builtins_impls.collections.ClaroTuple;
import com.google.common.collect.ImmutableList;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TupleExpr extends Expr {

  private final ImmutableList<Expr> tupleValues;
  private Types.TupleType type;

  public TupleExpr(ImmutableList<Expr> tupleValues, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.tupleValues = tupleValues;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    ImmutableList.Builder<Type> valueTypesBuilder = ImmutableList.builder();
    for (Expr expr : tupleValues) {
      valueTypesBuilder.add(expr.getValidatedExprType(scopedHeap));
    }
    this.type = Types.TupleType.forValueTypes(valueTypesBuilder.build());
    return type;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    AtomicReference<GeneratedJavaSource> tupleValsGenJavaSource =
        new AtomicReference<>(GeneratedJavaSource.forJavaSourceBody(new StringBuilder()));

    StringBuilder resJavaSourceBody = new StringBuilder();
    resJavaSourceBody.append("new ClaroTuple(");
    resJavaSourceBody.append(this.type.getJavaSourceClaroType());
    resJavaSourceBody.append(", ");
    resJavaSourceBody.append(
        this.tupleValues.stream()
            .map(expr -> {
              GeneratedJavaSource tupleValGenJavaSource = expr.generateJavaSourceOutput(scopedHeap);
              String tupleValJavaSourceString = tupleValGenJavaSource.javaSourceBody().toString();
              // We've consumed the javaSourceBody, it's safe to clear.
              tupleValGenJavaSource.javaSourceBody().setLength(0);
              // Now merge with the overall gen java source to track all of the static and preamble stmts.
              tupleValsGenJavaSource.set(tupleValsGenJavaSource.get().createMerged(tupleValGenJavaSource));

              return tupleValJavaSourceString;
            })
            .collect(Collectors.joining(", ")));
    resJavaSourceBody.append(")");

    return GeneratedJavaSource.forJavaSourceBody(resJavaSourceBody)
        .createMerged(tupleValsGenJavaSource.get());
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return new ClaroTuple(
        type,
        this.tupleValues.stream()
            .map(expr -> expr.generateInterpretedOutput(scopedHeap))
            .collect(ImmutableList.toImmutableList())
            .asList()
            .toArray()
    );
  }
}
