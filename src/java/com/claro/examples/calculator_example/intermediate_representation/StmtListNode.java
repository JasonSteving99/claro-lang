package com.claro.examples.calculator_example.intermediate_representation;

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


  @Override
  protected StringBuilder generateJavaSourceOutput() {
    StringBuilder res = this.getChildren().get(0).generateJavaSourceOutput();
    if (tail != null) {
      res.append(tail.generateJavaSourceOutput());
    }
    return res;
  }
}
