package com.claro.intermediate_representation.expressions.bool;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.HashMap;
import java.util.function.Supplier;

public class InBoolExpr extends BoolExpr {

  private final Expr lhs;
  private final Expr rhs;

  public InBoolExpr(Expr lhs, Expr rhs, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.lhs = lhs;
    this.rhs = rhs;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // rhs has to be a Map.
    Type rhsType = rhs.getValidatedExprType(scopedHeap);
    if (!rhsType.baseType().equals(BaseType.MAP)) {
      rhs.logTypeError(new ClaroTypeException(rhsType, BaseType.MAP));
    } else {
      // lhs has to be of the rhs's key type.
      lhs.assertExpectedExprType(scopedHeap, rhsType.parameterizedTypeArgs()
          .get(Types.MapType.PARAMETERIZED_TYPE_KEYS));
    }

    return Types.BOOLEAN;
  }

  @Override
  protected ImmutableSet<Type> getSupportedOperandTypes() {
    throw new IllegalStateException("Internal Compiler Error! This should be unreachable.");
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource lhsGeneratedJavaSource = this.lhs.generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource rhsGeneratedJavaSource = this.rhs.generateJavaSourceOutput(scopedHeap);

    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                "%s.containsKey(%s)",
                rhsGeneratedJavaSource.javaSourceBody(),
                lhsGeneratedJavaSource.javaSourceBody()
            )
        ));

    // We've already picked up the javaSourceBody, so we don't need it again.
    lhsGeneratedJavaSource.javaSourceBody().setLength(0);
    rhsGeneratedJavaSource.javaSourceBody().setLength(0);

    // Need to ensure that we pick up the static definitions from each child expr.
    return res.createMerged(lhsGeneratedJavaSource).createMerged(rhsGeneratedJavaSource);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((HashMap) this.rhs.generateInterpretedOutput(scopedHeap))
        .containsKey(this.lhs.generateInterpretedOutput(scopedHeap));
  }
}
