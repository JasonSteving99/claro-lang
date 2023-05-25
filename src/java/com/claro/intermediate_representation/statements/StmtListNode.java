package com.claro.intermediate_representation.statements;

import com.claro.ClaroParserException;
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
    // Type checking shouldn't be recursive over StmtListNode as it may cause stack overflow during compilation just
    // because there are a large number of statements. We don't want to fail during compilation for code that would
    // succeed at runtime.
    StmtListNode curr = this;
    while (curr != null) {
      // If the hidden variable tracking ReturnStmts is initialized in the current scope,
      // then it's indicating that this Stmt is located after a ReturnStmt which is invalid.
      if (scopedHeap.scopeStack.peek().initializedIdentifiers.stream()
          .anyMatch(i -> i.matches("\\$(.*RETURNS|BREAK|CONTINUE)"))) {
        // TODO(steving) Find some way to make this assertion in the CUP parsing phase itself. Once we start
        //  implementing better error messages with line numbers etc, I get a feeling that
        //  throwing this manner of error here will make it hard to get line numbers right.
        throw new ClaroParserException(
            "Unreachable statements following a return/break stmt are not allowed." + curr.getChildren().get(0));
      }

      Stmt currStmt = ((Stmt) curr.getChildren().get(0));
      if (currStmt instanceof ProcedureDefinitionStmt) {
        if (UsingBlockStmt.currentlyUsedBindings.isEmpty()) {
          // ProcedureDefinitionStmts were already validated during an earlier parsing phase, don't waste time
          // validating them again, skip them now.
        } else {
          // We need to disallow ProcedureDefinitionStmts from being used within a UsingBlockStmt even though
          // the grammar will allow it.
          ProcedureDefinitionStmt procedureDefinitionStmt = (ProcedureDefinitionStmt) currStmt;
          throw ClaroTypeException.forInvalidProcedureDefinitionWithinUsingBlock(
              procedureDefinitionStmt.procedureName,
              procedureDefinitionStmt.procedureTypeProvider.apply(procedureDefinitionStmt).resolveType(scopedHeap)
          );
        }
      } else if (currStmt instanceof ModuleDefinitionStmt) {
        // ModuleDefinitionStmts were already validated during an earlier parsing phase, don't waste time
        // validating them again, skip them now.
      } else {
        currStmt.assertExpectedExprTypes(scopedHeap);
      }

      // Move on to the next StmtListNode.
      curr = curr.tail;
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
    // Codegen shouldn't be recursive over StmtListNode as it may cause stack overflow during compilation just because
    // there are a large number of statements. We don't want to fail during compilation for code that would succeed at
    // runtime.
    StmtListNode curr = this;
    while (curr.tail != null) {
      curr = curr.tail;
      res = res.createMerged(
          ((Stmt) curr.getChildren().get(0)).generateJavaSourceOutput(scopedHeap, this.generatedJavaClassName));
    }
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Simply execute the statements. Statements don't return values UNLESS we're in a procedure scope and the
    // Stmt we're executing happens to have run a ReturnStmt in which case...return that value.
    Object maybeReturnValue = this.getChildren().get(0).generateInterpretedOutput(scopedHeap);
    if (tail != null && maybeReturnValue == null) {
      maybeReturnValue = tail.generateInterpretedOutput(scopedHeap);
    }
    // This return value is probably `null` unless the last executed Stmt happened to be a ReturnStmt.
    return maybeReturnValue;
  }
}
