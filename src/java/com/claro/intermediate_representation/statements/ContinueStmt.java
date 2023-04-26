package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;

import java.util.function.Supplier;

public class ContinueStmt extends Stmt {
  private final Expr syntheticExprForErrorLogging;

  public ContinueStmt(Supplier<String> currenLineSupplier, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of());
    this.syntheticExprForErrorLogging =
        new Expr(ImmutableList.of(), currenLineSupplier, currentLineNumber, startCol, endCol) {
          @Override
          public Type getValidatedExprType(ScopedHeap scopedHeap) {
            return null;
          }

          @Override
          public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
            return null;
          }
        };
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Simply need to validate that I'm actually within a looping construct (repeat/while/for).
    if (!InternalStaticStateUtil.LoopingConstructs_withinLoopingConstructBody) {
      this.syntheticExprForErrorLogging.logTypeError(ClaroTypeException.forIllegalUseOfBreakStmtOutsideLoopingConstruct());
    }

    // Mark the hidden variable flag tracking whether there's a return in every branch of this procedure
    // as initialized on this branch.
    scopedHeap.initializeIdentifier("$CONTINUE");
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return GeneratedJavaSource.forJavaSourceBody(new StringBuilder("continue;\n"));
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Eventually need to impl continue when I come back to adding support for the interpreted backend.
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support `continue` in the interpreted backend just yet!");
  }
}
