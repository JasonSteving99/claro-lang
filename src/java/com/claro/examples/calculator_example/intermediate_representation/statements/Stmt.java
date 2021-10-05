package com.claro.examples.calculator_example.intermediate_representation.statements;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.Node;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.google.common.collect.ImmutableList;

public abstract class Stmt extends Node {
  public Stmt(ImmutableList<Node> children) {
    super(children);
  }

  // Exprs should override this method if they need to do something fancier like supporting multiple contexts (e.g. an
  // int Expr should be able to just represent itself as a double Expr). In that case, this impl, should actually
  // modify internal state such that when generate*Output is called afterwards, it will produce the expected type.
  public abstract void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException;

  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap, String generatedJavaClassName) {
    // Most Stmts don't actually need this data so we'll drop the class name.
    return generateJavaSourceOutput(scopedHeap);
  }
}
