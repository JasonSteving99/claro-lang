package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.*;
import com.claro.intermediate_representation.types.impls.builtins_impls.collections.ClaroTuple;
import com.google.common.collect.ImmutableList;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TupleExpr extends Expr {

  private final ImmutableList<Expr> tupleValues;
  private final boolean isMutable;
  private Types.TupleType assertedType;
  private Types.TupleType type;

  public TupleExpr(ImmutableList<Expr> tupleValues, boolean isMutable, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.tupleValues = tupleValues;
    this.isMutable = isMutable;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    expectedExprType = TypeProvider.Util.maybeDereferenceAliasSelfReference(expectedExprType, scopedHeap);
    // Know for a fact this is a Tuple, the user can't assert anything else.
    if (!expectedExprType.baseType().equals(BaseType.TUPLE)) {
      logTypeError(new ClaroTypeException(BaseType.TUPLE, expectedExprType));
      return;
    }
    this.assertedType = (Types.TupleType) expectedExprType;
    super.assertExpectedExprType(scopedHeap, expectedExprType);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (this.assertedType != null) { // Type was asserted by programmer, we have to check that the actual types align.
      if (this.tupleValues.size() != this.assertedType.getValueTypes().size()) {
        logTypeError(
            ClaroTypeException.forTupleHasUnexpectedSize(
                this.assertedType.getValueTypes().size(), this.tupleValues.size(), this.assertedType));
      } else {
        for (int i = 0; i < this.assertedType.getValueTypes().size(); i++) {
          this.tupleValues.get(i).assertExpectedExprType(scopedHeap, this.assertedType.getValueTypes().get(i));
        }
      }
      this.type = Types.TupleType.forValueTypes(this.assertedType.getValueTypes(), this.isMutable);
    } else { // Type wasn't asserted by programmer, we get to infer it.
      ImmutableList.Builder<Type> valueTypesBuilder = ImmutableList.builder();
      for (Expr expr : tupleValues) {
        valueTypesBuilder.add(expr.getValidatedExprType(scopedHeap));
      }
      this.type = Types.TupleType.forValueTypes(valueTypesBuilder.build(), isMutable);
    }
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
