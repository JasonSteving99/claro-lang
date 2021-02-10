package com.claro.examples.calculator_example.intermediate_representation;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;
import java.util.HashSet;

public class AssignmentStmt extends Stmt {

  // TODO(steving) This should just be a child IdentifierReferenceTerm passed to the superclass.
  private final String IDENTIFIER;
  private boolean declaration = false;

  // TODO(steving) Update the usedSymbolSet to actually count references instead, warn on reference counts < 2, assignment can be reference count #1.
  public AssignmentStmt(String identifier, Expr e, HashSet<String> symbolSet, HashSet<String> usedSymbolSet) {
    super(ImmutableList.of(e));
    this.IDENTIFIER = identifier;
    // TODO(steving) Note this is happening on the way *up* the AST while building bottom-up during parsing stage...eventually this should happen on the way down after the full AST is constructed in order to better support nested scopes.
    if (!symbolSet.contains(identifier)) {
      this.declaration = true;
      symbolSet.add(identifier);
    }
  }

  @Override
  protected StringBuilder generateJavaSourceOutput() {
    StringBuilder res = new StringBuilder();
    if (this.declaration) {
      // First time we're seeing the variable, so declare it.
      res.append(String.format("double %s;\n", this.IDENTIFIER));
    }
    res.append(String.format("%s = %s;\n", this.IDENTIFIER, this.getChildren().get(0).generateJavaSourceOutput().toString()));
    return res;
  }

  @Override
  protected Object generateInterpretedOutput(HashMap<String, Object> heap) {
    // Put the computed value of this identifier directly in the heap.
    heap.put(this.IDENTIFIER, this.getChildren().get(0).generateInterpretedOutput(heap));
    return null;
  }
}
