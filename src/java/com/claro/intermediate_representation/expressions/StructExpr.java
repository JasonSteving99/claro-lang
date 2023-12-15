package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.*;
import com.google.common.collect.ImmutableList;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class StructExpr extends Expr {

  private final ImmutableList<String> fieldNames;
  private final ImmutableList<Expr> fieldValues;
  private final boolean isMutable;
  private Types.StructType assertedType;
  private Types.StructType type;

  public StructExpr(ImmutableList<String> fieldNames, ImmutableList<Expr> fieldValues, boolean isMutable, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.fieldNames = fieldNames;
    this.fieldValues = fieldValues;
    this.isMutable = isMutable;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    expectedExprType = TypeProvider.Util.maybeDereferenceAliasSelfReference(expectedExprType, scopedHeap);
    // Know for a fact this is a Struct, the user can't assert anything else.
    if (!expectedExprType.baseType().equals(BaseType.STRUCT)) {
      logTypeError(new ClaroTypeException(BaseType.STRUCT, expectedExprType));
      return;
    }
    this.assertedType = (Types.StructType) expectedExprType;
    super.assertExpectedExprType(scopedHeap, expectedExprType);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (this.assertedType != null) { // Type was asserted by programmer, we have to check that the actual types align.
      if (!this.fieldNames.equals(this.assertedType.getFieldNames())) {
        logTypeError(
            new ClaroTypeException(
                Types.StructType.forFieldTypes(
                    this.fieldNames,
                    this.fieldValues.stream()
                        .map(expr -> {
                          try {
                            return expr.getValidatedExprType(scopedHeap);
                          } catch (ClaroTypeException e) {
                            expr.logTypeError(e);
                            return Types.UNKNOWABLE;
                          }
                        })
                        .collect(ImmutableList.toImmutableList()),
                    this.isMutable
                ),
                this.assertedType
            ));
      } else {
        // Have all the expected fields represented, but need to ensure that the values are all of the correct
        // types for each respective field.
        for (int i = 0; i < this.assertedType.getFieldTypes().size(); i++) {
          this.fieldValues.get(i).assertExpectedExprType(scopedHeap, this.assertedType.getFieldTypes().get(i));
        }
      }
      this.type = Types.StructType.forFieldTypes(
          this.assertedType.getFieldNames(),
          this.assertedType.getFieldTypes(),
          this.isMutable
      );
    } else { // Type wasn't asserted by programmer, we get to infer it.
      this.type =
          Types.StructType.forFieldTypes(
              this.fieldNames,
              this.fieldValues.stream()
                  .map(expr -> {
                    try {
                      return expr.getValidatedExprType(scopedHeap);
                    } catch (ClaroTypeException e) {
                      expr.logTypeError(e);
                      return Types.UNKNOWABLE;
                    }
                  })
                  .collect(ImmutableList.toImmutableList()),
              this.isMutable
          );
    }
    return type;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    AtomicReference<GeneratedJavaSource> structValsGenJavaSource =
        new AtomicReference<>(GeneratedJavaSource.forJavaSourceBody(new StringBuilder()));

    StringBuilder resJavaSourceBody = new StringBuilder("new ");
    resJavaSourceBody.append(this.type.getJavaSourceType()).append("(");
    resJavaSourceBody.append(
        this.fieldValues.stream()
            .map(expr -> {
              GeneratedJavaSource fieldValGenJavaSource = expr.generateJavaSourceOutput(scopedHeap);
              String fieldValJavaSourceString = fieldValGenJavaSource.javaSourceBody().toString();
              // We've consumed the javaSourceBody, it's safe to clear.
              fieldValGenJavaSource.javaSourceBody().setLength(0);
              // Now merge with the overall gen java source to track all of the static and preamble stmts.
              structValsGenJavaSource.set(structValsGenJavaSource.get().createMerged(fieldValGenJavaSource));

              return fieldValJavaSourceString;
            })
            .collect(Collectors.joining(", ")));
    resJavaSourceBody.append(")");

    return GeneratedJavaSource.forJavaSourceBody(resJavaSourceBody)
        .createMerged(structValsGenJavaSource.get());
  }
}
