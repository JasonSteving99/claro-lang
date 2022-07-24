package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.Node;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.google.common.collect.ImmutableList;

public abstract class Stmt extends Node {
  private static StringBuilder prefixJavaSourceStmts = new StringBuilder();

  public Stmt(ImmutableList<Node> children) {
    super(children);
  }

  // Exprs should override this method if they need to do something fancier like supporting multiple contexts (e.g. an
  // int Expr should be able to just represent itself as a double Expr). In that case, this impl, should actually
  // modify internal state such that when generate*Output is called afterwards, it will produce the expected type.
  public abstract void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException;

  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap, String unusedGeneratedJavaClassName) {
    // Before we do any codegen for this current Stmt, we know that any existing Stmt prefix Stmts
    // indicate that this Stmt is nested within an expression (e.g. in a lambda body), and the
    // existing prefix Stmts are related to other sub-statements in this expression. We don't want to step
    // on the toes of those other sub-stmts in this expression, so we'll hold onto them off to the side,
    // for now, so that we can reconstruct it later.
    StringBuilder existingPrefixJavaSourceStmts = Stmt.prefixJavaSourceStmts;
    // Reset to empty, so if any prefix stmts come up during codegen of this current stmt, we'll know that
    // they're this Stmt's responsibility to deal with.
    Stmt.prefixJavaSourceStmts = new StringBuilder();

    // Most Stmts don't actually need this data so we'll drop the class name.
    GeneratedJavaSource generatedJavaSource = generateJavaSourceOutput(scopedHeap);

    // Before we return this GeneratedJavaSource, we need to ensure that any potential prefix Stmts requested
    // by sub-Exprs of this current Stmt get prepended to this returned GeneratedJavaSource output.
    if (Stmt.prefixJavaSourceStmts.length() > 0) {
      generatedJavaSource = generatedJavaSource.withNewJavaSourceBody(
          new StringBuilder(Stmt.prefixJavaSourceStmts.toString()).append(generatedJavaSource.javaSourceBody()));

    }

    // Now recover the old state to ready the prefix StringBuilder for either the next Stmt or the next sub-Stmt.
    prefixJavaSourceStmts = existingPrefixJavaSourceStmts;

    return generatedJavaSource;
  }

  // This method allows Stmt sub-Exprs to inject a Stmt that should be generated before the CURRENT Stmt
  // for which JavaSource codegen is currently happening. This enabled situations such as a LambdaExpr
  // needing to generate a procedure class on the line before the current statement so that in the place
  // where the lambda was passed as an Expr, a reference to that class is available in scope.
  public static void addGeneratedJavaSourceStmtBeforeCurrentStmt(String prefixGeneratedJavaSourceStmt) {
    Stmt.prefixJavaSourceStmts.append(prefixGeneratedJavaSourceStmt);
  }

  // In some situations these prefix java source statements may be consumed early.
  public static StringBuilder consumeGeneratedJavaSourceStmtsBeforeCurrentStmt() {
    StringBuilder res = Stmt.prefixJavaSourceStmts;
    Stmt.prefixJavaSourceStmts = new StringBuilder();
    return res;
  }
}
