package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.builtins_impls.collections.ClaroTuple;
import com.google.common.collect.ImmutableList;

import java.util.stream.Collectors;

public class TupleExpr extends Expr {

  private final ImmutableList<Expr> tupleValues;
  private Types.TupleType type;

  public TupleExpr(ImmutableList<Expr> tupleValues) {
    super(ImmutableList.of());
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
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    StringBuilder res = new StringBuilder();
    res.append("new ClaroTuple(");
    res.append(this.type.getJavaSourceClaroType());
    res.append(", ");
    res.append(
        this.tupleValues.stream()
            .map(expr -> expr.generateJavaSourceBodyOutput(scopedHeap))
            .collect(Collectors.joining(", ")));
    res.append(")");
    return res;
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
