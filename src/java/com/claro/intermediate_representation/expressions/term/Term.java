package com.claro.intermediate_representation.expressions.term;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.google.common.collect.ImmutableList;

import java.util.function.Supplier;

public abstract class Term extends Expr {
  public Term(Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    // Terms are the bottom of the grammar. No more children.
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
  }

  // Override this method for Terms that actually need to do something with this ScopedHeap.
  @Override
  public abstract Type getValidatedExprType(ScopedHeap unusedScopedHeap) throws ClaroTypeException;

  // Convenience method to make life easy for synthetic nodes that are needed simply for the sake of hardcoding some
  // type checking case.
  public static Term getDummyTerm(Type dummyType, Object dummyTermValue) {
    return new Term(null, -1, -1, -1) {
      @Override
      public Type getValidatedExprType(ScopedHeap unusedScopedHeap) throws ClaroTypeException {
        return dummyType;
      }

      @Override
      public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
        return dummyTermValue;
      }
    };
  }
}
