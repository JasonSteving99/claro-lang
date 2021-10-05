package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.Node;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.ConcreteTypes;
import com.claro.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public abstract class Expr extends Node {
  protected boolean acceptUndecided = false;

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
  public abstract Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException;

  // Exprs should override this method if they need to do something fancier like supporting multiple contexts (e.g. an
  // int Expr should be able to just represent itself as a double Expr). In that case, this impl, should actually
  // modify internal state such that when generate*Output is called afterwards, it will produce the expected type.
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType)
      throws ClaroTypeException {
    Type validatedExprType = this.getValidatedExprType(scopedHeap);
    this.assertNoUndecidedTypeLeak(validatedExprType, expectedExprType);

    if (!validatedExprType.equals(expectedExprType)) {
      throw new ClaroTypeException(validatedExprType, expectedExprType);
    }
  }

  // TODO(steving) Making this static was definitely a mistake... make this non-static.
  protected static final void assertSupportedExprType(
      Type actualExprType, ImmutableSet<Type> supportedExprTypes) throws ClaroTypeException {
    // TODO(steving) Once this is no longer unnecessarily static, replace this logic with a call to this.assertNoUndecidedTypeLeak();
    if (actualExprType.equals(ConcreteTypes.UNDECIDED)) {
      throw ClaroTypeException.forUndecidedTypeLeak(supportedExprTypes);
    }

    if (!supportedExprTypes.contains(actualExprType)) {
      throw new ClaroTypeException(actualExprType, supportedExprTypes);
    }
  }

  public final void assertExpectedBaseType(ScopedHeap scopedHeap, BaseType expectedBaseType)
      throws ClaroTypeException {
    Type validatedExprType = this.getValidatedExprType(scopedHeap);
    this.assertNoUndecidedTypeLeak(validatedExprType, expectedBaseType);

    if (!validatedExprType.baseType().equals(expectedBaseType)) {
      throw new ClaroTypeException(validatedExprType, expectedBaseType);
    }
  }

  public final void assertSupportedExprBaseType(ScopedHeap scopedHeap, ImmutableSet<BaseType> supportedBaseTypes)
      throws ClaroTypeException {
    Type validatedExprType = this.getValidatedExprType(scopedHeap);
    this.assertNoUndecidedTypeLeak(validatedExprType, supportedBaseTypes);

    if (!supportedBaseTypes.contains(validatedExprType.baseType())) {
      throw new ClaroTypeException(validatedExprType, supportedBaseTypes);
    }
  }

  public final void setAcceptUndecided(boolean acceptUndecided) {
    this.acceptUndecided = acceptUndecided;
  }

  // This method will be used by ALL assert*Type methods above to ensure that we're never leaking an UNDECIDED type
  // where we don't explicitly allow it.
  protected final <T> void assertNoUndecidedTypeLeak(
      Type exprType, T contextuallyExpectedType) throws ClaroTypeException {
    if (!this.acceptUndecided) {
      if (exprType.equals(ConcreteTypes.UNDECIDED)) {
        throw ClaroTypeException.forUndecidedTypeLeak(contextuallyExpectedType);
      }
    }
  }

  public final GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return GeneratedJavaSource.forJavaSourceBody(generateJavaSourceBodyOutput(scopedHeap));
  }

  public abstract StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap);
}
