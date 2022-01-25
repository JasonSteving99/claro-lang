package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.Node;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.google.common.collect.ImmutableList;

public class StmtListNode extends Node {
  public StmtListNode tail = null;

  private String generatedJavaClassName;

  // This is the last stmt in the list.
  public StmtListNode(Stmt stmt) {
    super(ImmutableList.of(stmt));
  }

  // This is not the last statement in the list. Use this constructor to build a list *backwards*...
  public StmtListNode(Stmt head, StmtListNode tail) {
    this(head);
    // Weird linked list in the middle of our AST...
    this.tail = tail;
  }

  // Called after complete construction of AST-IR, but before evaluating any program values.
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    ((Stmt) this.getChildren().get(0)).assertExpectedExprTypes(scopedHeap);
    if (tail != null) {
      tail.assertExpectedExprTypes(scopedHeap);
    }
  }

  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap, String generatedJavaClassName) {
    this.generatedJavaClassName = generatedJavaClassName;
    return generateJavaSourceOutput(scopedHeap);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res =
        ((Stmt) this.getChildren().get(0)).generateJavaSourceOutput(scopedHeap, this.generatedJavaClassName);
    if (tail != null) {
      GeneratedJavaSource tailGeneratedJavaSource =
          tail.generateJavaSourceOutput(scopedHeap, this.generatedJavaClassName);
      res.javaSourceBody().append(tailGeneratedJavaSource.javaSourceBody());
      if (tailGeneratedJavaSource.optionalStaticDefinitions().isPresent()) {
        // We know that the static preamble only shows up if the static definitions are also present.
        if (res.optionalStaticDefinitions().isPresent()) {
          res.optionalStaticDefinitions().get().append(tailGeneratedJavaSource.optionalStaticDefinitions().get());
          if (tailGeneratedJavaSource.optionalStaticPreambleStmts().isPresent()) {
            if (res.optionalStaticPreambleStmts().isPresent()) {
              res.optionalStaticPreambleStmts().get()
                  .append(tailGeneratedJavaSource.optionalStaticPreambleStmts().get());
            } else {
              res = GeneratedJavaSource.create(
                  res.javaSourceBody(),
                  res.optionalStaticDefinitions().get(),
                  tailGeneratedJavaSource.optionalStaticPreambleStmts().get()
              );
            }
          }
        } else {
          res = GeneratedJavaSource.create(
              res.javaSourceBody(),
              tailGeneratedJavaSource.optionalStaticDefinitions().get(),
              tailGeneratedJavaSource.optionalStaticPreambleStmts().get()
          );
        }
      }
    }
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Simply execute the statements. Statements don't return values UNLESS we're in a procedure scope and the
    // Stmt we're executing happens to be a ReturnStmt in which case...return that value. We don't want to slow
    // everything down by checking every single Stmt to see if it is of type ReturnStmt, or if the value is
    // non-null, so we'll just depend on the fact that ReturnStmts should necessarily be the final stmt in their
    // StmtList (validated elsewhere) to just hold onto this current Stmt's return value and return it at the end.
    Object maybeReturnValue = this.getChildren().get(0).generateInterpretedOutput(scopedHeap);
    if (tail != null) {
      tail.generateInterpretedOutput(scopedHeap);
//      maybeReturnValue = tail.generateInterpretedOutput(scopedHeap);
    }
    // This return value is probably `null` unless the last executed Stmt happened to be a ReturnStmt.
    return maybeReturnValue;
  }
}
