package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;

// TODO(steving) Implement tuples as an immutable array of references.
public class TupleDefinitionStmt extends Stmt {
  public TupleDefinitionStmt(ImmutableList<Type> fieldTypes) {
    super(ImmutableList.of());
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {

  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
//    throw new NotImplementedException();
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return null;
  }
}
