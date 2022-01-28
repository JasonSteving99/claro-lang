package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.Node;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.google.common.collect.ImmutableList;

public abstract class Stmt extends Node {
  private static final StringBuilder prefixJavaSourceStmts = new StringBuilder();

  public Stmt(ImmutableList<Node> children) {
    super(children);
  }

  // Exprs should override this method if they need to do something fancier like supporting multiple contexts (e.g. an
  // int Expr should be able to just represent itself as a double Expr). In that case, this impl, should actually
  // modify internal state such that when generate*Output is called afterwards, it will produce the expected type.
  public abstract void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException;

  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap, String generatedJavaClassName) {
    // Most Stmts don't actually need this data so we'll drop the class name.
    GeneratedJavaSource generatedJavaSource = generateJavaSourceOutput(scopedHeap);

    // Before we return this GeneratedJavaSource, we need to ensure that any potential prefix Stmts requested
    // by sub-Exprs of this current Stmt get prepended to this returned GeneratedJavaSource output.
    if (prefixJavaSourceStmts.length() > 0) {
      generatedJavaSource = generatedJavaSource.withNewJavaSourceBody(
          new StringBuilder(prefixJavaSourceStmts.toString()).append(generatedJavaSource.javaSourceBody()));

      // Ready the prefix StringBuilder for the next Stmt.
      prefixJavaSourceStmts.delete(0, prefixJavaSourceStmts.length());
    }

    return generatedJavaSource;
  }

  // This method allows Stmt sub-Exprs to inject a Stmt that should be generated before the CURRENT Stmt
  // for which JavaSource codegen is currently happening. This enabled situations such as a LambdaExpr
  // needing to generate a procedure class on the line before the current statement so that in the place
  // where the lambda was passed as an Expr, a reference to that class is available in scope.
  public static void addGeneratedJavaSourceStmtBeforeCurrentStmt(String prefixGeneratedJavaSourceStmt) {
    Stmt.prefixJavaSourceStmts.append(prefixGeneratedJavaSourceStmt);
  }
}
