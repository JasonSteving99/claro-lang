package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.google.common.collect.ImmutableList;

public class StmtListNode extends Node {
  private StmtListNode tail = null;

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
  protected void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    ((Stmt) this.getChildren().get(0)).assertExpectedExprTypes(scopedHeap);
    if (tail != null) {
      tail.assertExpectedExprTypes(scopedHeap);
    }
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder res = this.getChildren().get(0).generateJavaSourceOutput(scopedHeap);
    if (tail != null) {
      res.append(tail.generateJavaSourceOutput(scopedHeap));
    }
    return res;
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Simply execute the statements. Statements don't return values.
    this.getChildren().get(0).generateInterpretedOutput(scopedHeap);
    if (tail != null) {
      tail.generateInterpretedOutput(scopedHeap);
    }
    return null;
  }
}
