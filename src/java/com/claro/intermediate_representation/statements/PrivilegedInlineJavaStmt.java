package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.google.common.collect.ImmutableList;

public class PrivilegedInlineJavaStmt extends Stmt {
  private final String java;

  public PrivilegedInlineJavaStmt(String java) {
    super(ImmutableList.of());
    this.java = java;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Somehow this must've been allowed by the Parser, so the only thing that needs to happen here is just
    // synthetically mark *every* variable in the current scope as initialized/used.
    scopedHeap.scopeStack.peek().scopedSymbolTable.keySet().forEach(
        identifier -> {
          scopedHeap.markIdentifierUsed(identifier);
          scopedHeap.initializeIdentifier(identifier);
        }
    );
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Somehow this must've been allowed by the Parser, so the only thing that needs to happen here is just
    // synthetically mark *every* variable in the current scope as initialized/used.
    scopedHeap.scopeStack.peek().scopedSymbolTable.keySet().forEach(
        identifier -> {
          scopedHeap.markIdentifierUsed(identifier);
          scopedHeap.initializeIdentifier(identifier);
        }
    );
    return GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder("// BEGIN PRIVILEGED INLINE JAVA\n")
            .append(this.java)
            .append("// END PRIVILEGED INLINE JAVA\n"));
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    throw new RuntimeException("Internal Compiler Error! Privileged Inline Java is not supported in the Interpreted backend!");
  }
}
