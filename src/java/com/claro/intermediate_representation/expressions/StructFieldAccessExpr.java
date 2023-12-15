package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

import java.util.function.Supplier;

public class StructFieldAccessExpr extends Expr {
  private final Expr expr;
  private final String fieldName;
  public Types.StructType validatedStructType;

  // To simplify parsing, I'm reusing this same node for struct field assignment as well as field reading. This
  // distinction has an impact on codegen, however, so I need to track that here. Default to read, but write parsing
  // will update this.
  public boolean codegenForRead = true;

  public StructFieldAccessExpr(Expr expr, String fieldName, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.expr = expr;
    this.fieldName = fieldName;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Type exprType = this.expr.getValidatedExprType(scopedHeap);
    if (!exprType.baseType().equals(BaseType.STRUCT)) {
      this.expr.logTypeError(new ClaroTypeException(exprType, BaseType.STRUCT));
      return Types.UNKNOWABLE;
    }
    this.validatedStructType = (Types.StructType) exprType;
    if (!this.validatedStructType.getFieldNames().contains(this.fieldName)) {
      throw ClaroTypeException.forInvalidStructFieldAccessForNonExistentField(this.validatedStructType, this.fieldName);
    }
    return this.validatedStructType.getFieldTypes()
        .get(this.validatedStructType.getFieldNames().indexOf(this.fieldName));
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    int fieldIndex = this.validatedStructType.getFieldNames().indexOf(this.fieldName);

    GeneratedJavaSource exprCodegen = this.expr.generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource res =
        GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder().append(exprCodegen.javaSourceBody().toString()).append(".").append(this.fieldName));

    // Already consumed this java source above.
    exprCodegen.javaSourceBody().setLength(0);

    return exprCodegen.createMerged(res);
  }
}
