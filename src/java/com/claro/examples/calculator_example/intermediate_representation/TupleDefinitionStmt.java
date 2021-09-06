package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;

// TODO(steving) Implement tuples as an immutable array of references.
public class TupleDefinitionStmt extends Stmt {
  public TupleDefinitionStmt(ImmutableList<Type> fieldTypes) {
    super(ImmutableList.of());
  }

  @Override
  protected void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {

  }

  @Override
  protected GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
//    throw new NotImplementedException();
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return null;
  }
}
