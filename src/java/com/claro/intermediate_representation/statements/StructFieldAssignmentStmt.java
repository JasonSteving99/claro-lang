package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.StructFieldAccessExpr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;

public class StructFieldAssignmentStmt extends Stmt {
  private final StructFieldAccessExpr fieldAccessExpr;
  private final Expr assignedValExpr;

  public StructFieldAssignmentStmt(StructFieldAccessExpr fieldAccessExpr, Expr assignedValExpr) {
    super(ImmutableList.of());
    this.fieldAccessExpr = fieldAccessExpr;
    this.assignedValExpr = assignedValExpr;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // First things first, assert that the fieldAccessExpr was valid and hold onto the type of the field.
    Type validatedFieldType = this.fieldAccessExpr.getValidatedExprType(scopedHeap);

    // Then, we also need to validate that the struct we're dealing with is actually in fact mutable.
    if (!this.fieldAccessExpr.validatedStructType.isMutable()) {
      throw ClaroTypeException.forIllegalMutationAttemptOnImmutableValue(
          this.fieldAccessExpr.validatedStructType,
          this.fieldAccessExpr.validatedStructType.toShallowlyMutableVariant()
      );
    }

    // Then, the assignedValExpr better have the same type as the field it's being assigned to. That's it!
    this.assignedValExpr.assertExpectedExprType(scopedHeap, validatedFieldType);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = this.fieldAccessExpr.generateJavaSourceOutput(scopedHeap);
    res.javaSourceBody().append(" = ");

    GeneratedJavaSource assignedValExprCodegen = this.assignedValExpr.generateJavaSourceOutput(scopedHeap);
    res.javaSourceBody()
        .append(assignedValExprCodegen.javaSourceBody().toString())
        .append(";\n");
    // Now that the assignedValExprCodegen's javaSourceBody has been consumed, we're done with it.
    assignedValExprCodegen.javaSourceBody().setLength(0);

    // Make sure that the returned codegen maintains the static preamble codegen from the assigned expr.
    return res.createMerged(assignedValExprCodegen);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    throw new RuntimeException("Internal Compiler Error: Claro doesn't support struct assignment in the interpreted backend yet!");
  }
}
