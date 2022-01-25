package com.claro.intermediate_representation.statements;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.TypeProvider;
import com.google.common.collect.ImmutableList;

import java.util.concurrent.atomic.AtomicReference;

public class ReturnStmt extends Stmt {
  // Unfortunately, because CUP is a context free grammar (or some other reason, look I'm no expert),
  // it's not possible to know the type that we should be returning when the ReturnStmt is identified
  // during bottom-up parsing. So it needs to be set during the top-down AST traversal.
  private AtomicReference<TypeProvider> expectedTypeProvider;

  public ReturnStmt(Expr returnExpr, AtomicReference<TypeProvider> expectedTypeProvider) {
    super(ImmutableList.of(returnExpr));
    this.expectedTypeProvider = expectedTypeProvider;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (expectedTypeProvider == null || expectedTypeProvider.get() == null) {
      throw new ClaroParserException("Invalid usage of `return` unexpected.");
    }
    ((Expr) getChildren().get(0)).assertExpectedExprType(scopedHeap, expectedTypeProvider.get()
        .resolveType(scopedHeap));
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                "return %s;",
                ((Expr) getChildren().get(0)).generateJavaSourceBodyOutput(scopedHeap)
            )
        ));
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // Acheiving this return Stmt without GOTO....:
    // I'm super hyped about this trick. Stmts by definition don't have a value, but I can leverage that fact
    // to know that all other Claro Stmts will return null from this method to establish an EXCEPTION to this
    // general rule for the case of the ReturnStmt. The ReturnStmt will be the *only* Stmt in the language
    // allowed to return a non-null value from this method. In that way, I have a convenient bit of information
    // to now branch on in StmtListNode::generateInterpretedOutput to return early with any non-null value
    // returned from any Stmt it executes thereby short-circuiting the rest of the list as desired. When this
    // happens, we may run into recursive nesting situations caused by branching stmts (at the time of writing
    // this, that's just if-else chains and while loops). Those branch sites must also include logic to return
    // a value if their contained StmtLists return a value, thereby recursively propagating the return signal
    // upward until the top-level StmtList returns to the procedure definition implemented by the
    // ProcedureDefinitionStmt::generateInterpretedOutput. To act as the base case to this entire recursive
    // process, the Procedure implementation must treat whatever is returned by the body StmtList as the return
    // type of the procedure if the procedure declares a return type. All of this will require a very significant
    // lift to implement branch analysis to determine whether or not a function with a declared return type
    // definitely returns in all of its branches.
    return getChildren().get(0).generateInterpretedOutput(scopedHeap);
  }
}
