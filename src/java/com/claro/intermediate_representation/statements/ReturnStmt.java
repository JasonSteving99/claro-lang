package com.claro.intermediate_representation.statements;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.TypeProvider;
import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class ReturnStmt extends Stmt {
  // Unfortunately, because CUP is a context free grammar (or some other reason, look I'm no expert),
  // it's not possible to know the type that we should be returning when the ReturnStmt is identified
  // during bottom-up parsing. So it needs to be set during the top-down AST traversal.
  private AtomicReference<TypeProvider> expectedTypeProvider;
  // ReturnStmts should only be valid w/in Procedure scopes, so during top-down AST traversal once we enter
  // a definition scope of a (non-Consumer) Procedure, this should be set to true in order to allow the
  // ReturnStmt, and will reset it to false upon leaving the Procedure definition scope. If a ReturnStmt
  // is reached during the type validation phase while this boolean is false, then that will let us know that
  // we are outside of a Procedure scope and this is an invalid ReturnStmt.
  public static Optional<String> withinProcedureScope = Optional.empty();
  public static boolean supportReturnStmt = false;

  public ReturnStmt(Expr returnExpr, AtomicReference<TypeProvider> expectedTypeProvider) {
    super(ImmutableList.of(returnExpr));
    this.expectedTypeProvider = expectedTypeProvider;
  }

  public static void enterProcedureScope(String procedureName, boolean allowReturnStmts) {
    ReturnStmt.withinProcedureScope = Optional.of(procedureName);
    ReturnStmt.supportReturnStmt = allowReturnStmts;
  }

  public static void exitProcedureScope() {
    ReturnStmt.withinProcedureScope = Optional.empty();
    ReturnStmt.supportReturnStmt = false;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (!(withinProcedureScope.isPresent() && supportReturnStmt)) {
      String invalidReturnReason;
      if (withinProcedureScope.isPresent()) {
        invalidReturnReason = String.format(
            "Invalid usage of `return` in a body of procedure <%s> that does not provide output.",
            withinProcedureScope.get()
        );
      } else {
        invalidReturnReason = "Invalid usage of `return` outside of a procedure body.";
      }
      throw new ClaroParserException(invalidReturnReason);
    }
    ((Expr) getChildren().get(0))
        .assertExpectedExprType(scopedHeap, expectedTypeProvider.get().resolveType(scopedHeap));
    // Mark the hidden variable flag tracking whether there's a return in every branch of this procedure
    // as initialized on this branch.
    scopedHeap.initializeIdentifier(String.format("$%sRETURNS", withinProcedureScope.get()));
  }

  // This is overridden simply to hide ReturnStmt from the custom logic in Stmt for this method, since
  // ReturnStmt is a very strange Stmt that shows up within other Stmt's sub-expressions.
  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap, String unusedGeneratedJavaClassName) {
    return generateJavaSourceOutput(scopedHeap);
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

  public void setExpectedTypeProvider(TypeProvider expectedTypeProvider) {
    this.expectedTypeProvider.set(expectedTypeProvider);
  }
}
