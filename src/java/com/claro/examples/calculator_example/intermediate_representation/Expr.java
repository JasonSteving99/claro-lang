package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;

public abstract class Expr extends Node {
  private final String INVALID_TYPE_ERROR_MESSAGE_FMT_STR = "Invalid type: expected <%s>, but found <%s>.";

  public Expr(ImmutableList<Node> children) {
    super(children);
  }

  // Exprs are Language constructs that have associated values, which mean that they are of a certain Type. All Expr
  // consumers in the Compiler *must* assert that the type is one that's expected so that we can report on type
  // mismatches at compile-time instead of failing at runtime.

  // Impls must return the validated type of the curr Expr. "Validated" in this context means that if sub-expressions
  // must be a certain type for the Expr to be semantically meaningful under a certain type interpretation, then it must
  // be asserted that all sub-expressions do in fact have that corresponding type. E.g. in the Expr log_2(...) the arg
  // expr must be compatible with type Double (either it's a Double, subclass of Double, or it's an Integer which can be
  // automatically upcasted to Double) and then this method would return Double for the overall log Expr, otherwise it
  // would throw a type exception.
  protected abstract Type getValidatedExprType(ScopedHeap scopedHeap);

  // Exprs should override this method if they need to do something fancier like supporting multiple contexts (e.g. an
  // int Expr should be able to just represent itself as a double Expr). In that case, this impl, should actually
  // modify internal state such that when generate*Output is called afterwards, it will produce the expected type.
  protected boolean assertExpectedExprType(ScopedHeap scopedHeap, Type type) {
    return this.getValidatedExprType(scopedHeap).equals(type);
  }

  protected final StringBuilder generateJavaSourceOutput(Type expectedExprType, ScopedHeap scopedHeap)
      throws Exception {
    if (!assertExpectedExprType(scopedHeap, expectedExprType)) {
      throw new ClaroTypeException(expectedExprType, getValidatedExprType(scopedHeap));
    }
    return generateJavaSourceOutput(scopedHeap);
  }

  protected final Object generateInterpretedOutput(Type expectedExprType, ScopedHeap scopedHeap) throws Exception {
    if (!assertExpectedExprType(scopedHeap, expectedExprType)) {
      throw new ClaroTypeException(expectedExprType, getValidatedExprType(scopedHeap));
    }
    return generateInterpretedOutput(scopedHeap);
  }
}
