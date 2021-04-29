package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.Stack;

public class IfStmt extends Stmt {
  // TODO(steving) This is a minor optimization so this is a low priority, but this method of constructing the
  // TODO(steving) conditionStack is actually really wasteful. There're a bunch of temporary IfStmt instances created
  // TODO(steving) just to be thrown away as the conditionStack is created bottom-up during AST construction. It's not
  // TODO(steving) that hard of a refactoring to just make a single IfStmt instance at the bottom-most condition clause
  // TODO(steving) and then just update that specific instance as you walk up the AST for the earlier condition clauses
  // TODO(steving) rather than calling `new` a bunch of times on the way up...For now, who cares, compiler
  // TODO(steving) efficiency-shmishency.

  // This whole Stack approach is genuinely just to avoid recursing over the condition list... the only reason I'm doing
  // that is because technically I might want to start thinking about the CompilerBackend's call stack management
  // because programs can get arbitrarily complex which might lead to an arbitrarily deep call stack while processing
  // the AST otherwise.
  private Stack<IfStmt> conditionStack;
  private Optional<StmtListNode> optionalTerminalElseClause = Optional.empty();

  // Constructor for "if" and "else if" statements that do have a condition to check.
  public IfStmt(Expr expr, StmtListNode stmtListNode) {
    super(ImmutableList.of(expr, stmtListNode));
    // Only initialize this to account for the case where there's an incomplete condition chain missing any "else-if"
    // clauses and/or missing any "else" clause. This will get replaced in the setNextCondition call if that's not the
    // case.
    conditionStack = new Stack<>();
    conditionStack.push(this);
  }

  // Build the condition stack bottom up, putting yourself on the top of the stack.
  public void setNextCondition(IfStmt nextCondition) {
    conditionStack = nextCondition.getConditionStack();
    conditionStack.push(this);
    // The downstream "else-if" stmt may have had a terminal else associated, make sure we propagate that reference up.
    optionalTerminalElseClause = nextCondition.optionalTerminalElseClause;
  }

  // This being called means that "this" IfStmt instance is the clause just before an "else" clause.
  public void setTerminalElseClause(StmtListNode terminalElseClause) {
    optionalTerminalElseClause = Optional.of(terminalElseClause);
  }

  private Stack<IfStmt> getConditionStack() {
    return conditionStack;
  }

  @Override
  protected void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    boolean enableBranchInspection = optionalTerminalElseClause.isPresent();

    for (IfStmt ifStmt : getConditionStack()) {
      ((Expr) ifStmt.getChildren().get(0)).assertExpectedExprType(scopedHeap, Types.BOOLEAN);

      scopedHeap.observeNewScope(enableBranchInspection);
      ((StmtListNode) ifStmt.getChildren().get(1)).assertExpectedExprTypes(scopedHeap);
      scopedHeap.exitCurrObservedScope(false);
    }
    if (optionalTerminalElseClause.isPresent()) {
      scopedHeap.observeNewScope(true);
      optionalTerminalElseClause.get().assertExpectedExprTypes(scopedHeap);
      scopedHeap.exitCurrObservedScope(true);
    }
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    StringBuilder res = new StringBuilder();

    // If it's a guarantee that at least one of these branches will execute, then we need to enable branch inspection.
    boolean enableBranchInspection = optionalTerminalElseClause.isPresent();

    // First thing first handle the one guaranteed present if-stmt.
    IfStmt initialIfStmt = this.getConditionStack().peek();
    appendIfConditionStmtJavaSource(initialIfStmt, scopedHeap, false, enableBranchInspection, res);

    // Now handle any remaining else-if-stmts. Iterate over, instead of pop-ing things off the stack, cuz in the
    // interpreted mode we need this Node to be reusable in the case of this being included for example in a loop or
    // function etc and I just want to match the behavior here to be less destructive in case for some reason it's ever
    // useful to code gen java source from the same IfStmt Node more than once...
    for (int i = getConditionStack().size() - 2; i >= 0; i--) {
      appendIfConditionStmtJavaSource(
          this.getConditionStack().get(i),
          scopedHeap,
          true,
          enableBranchInspection,
          res
      );
    }

    // Now handle the optional trailing else-stmt.
    optionalTerminalElseClause.ifPresent(
        terminalElseClauseStmtList
            -> {
          scopedHeap.enterNewScope();
          res.append(
              String.format(
                  "else {\n%s\n}",
                  terminalElseClauseStmtList.generateJavaSourceOutput(scopedHeap).toString()
              )
          );
          scopedHeap.exitCurrScope();
        }
    );

    // Add a final trailing newline to make the following code land after this condition chain.
    res.append("\n");

    return res;
  }

  private void appendIfConditionStmtJavaSource(
      IfStmt ifStmt,
      ScopedHeap scopedHeap,
      boolean elseIf,
      boolean enableBranchInspection,
      StringBuilder stringBuilder) {
    stringBuilder.append(
        String.format(
            elseIf ? "else if ( %s )" : "if ( %s )",
            ifStmt
                .getChildren()
                .get(0)
                .generateJavaSourceOutput(scopedHeap)
                .toString()
        )
    );
    // We've now entered a new scope.
    scopedHeap.enterNewScope();
    // Do work in this new scope.
    stringBuilder.append(
        String.format(
            " {\n%s\n} ",
            ifStmt.getChildren()
                .get(1)
                .generateJavaSourceOutput(scopedHeap)
                .toString()
        )
    );
    // And we're now leaving this scope.
    scopedHeap.exitCurrScope();
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Iterate over, instead of pop-ing things off the stack, cuz we need this Node to be reusable in the case of this
    // being included for example in a loop or function etc.
    for (int i = getConditionStack().size() - 1; i >= 0; i--) {
      IfStmt ifStmt = getConditionStack().get(i);
      if ((boolean) ifStmt.getChildren().get(0).generateInterpretedOutput(scopedHeap)) {
        // We've now entered a new scope.
        scopedHeap.enterNewScope();
        // Do work in this new scope.
        ifStmt.getChildren().get(1).generateInterpretedOutput(scopedHeap);
        // And we're now leaving this scope.
        scopedHeap.exitCurrScope();

        // Ok, we're done with this whole chain. Short-circuit and literally don't even look at the rest of the code.
        return null;
      }
    }

    // Didn't match any given condition, if there's an else clause, just run that code.
    optionalTerminalElseClause.ifPresent(
        terminalElseClauseStmtList -> terminalElseClauseStmtList.generateInterpretedOutput(scopedHeap));
    return null;
  }
}
