package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.BaseType;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

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
  protected abstract Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException;

  // Exprs should override this method if they need to do something fancier like supporting multiple contexts (e.g. an
  // int Expr should be able to just represent itself as a double Expr). In that case, this impl, should actually
  // modify internal state such that when generate*Output is called afterwards, it will produce the expected type.
  protected void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    Type validatedExprType = this.getValidatedExprType(scopedHeap);
    if (!validatedExprType.equals(expectedExprType)) {
      throw new ClaroTypeException(validatedExprType, expectedExprType);
    }
  }

  // TODO(steving) Making this static was definitely a mistake... make this non-static.
  protected static final void assertSupportedExprType(Type actualExprType, ImmutableSet<Type> supportedExprTypes)
      throws ClaroTypeException {
    if (!supportedExprTypes.contains(actualExprType)) {
      throw new ClaroTypeException(actualExprType, supportedExprTypes);
    }
  }

  protected final void assertExpectedBaseType(ScopedHeap scopedHeap, BaseType expectedBaseType)
      throws ClaroTypeException {
    Type validatedExprType = this.getValidatedExprType(scopedHeap);
    if (!validatedExprType.baseType().equals(expectedBaseType)) {
      throw new ClaroTypeException(validatedExprType, expectedBaseType);
    }
  }
}
