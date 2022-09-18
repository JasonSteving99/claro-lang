package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.runtime_utilities.ClaroRuntimeUtilities;
import com.google.common.collect.ImmutableList;

import java.util.function.Supplier;

public class CastExpr extends Expr {
  private final TypeProvider assertedTypeProvider;
  private final Expr castedExpr;
  private Type actualAssertedType;
  private boolean isDynamicCast;

  public CastExpr(TypeProvider assertedTypeProvider, Expr castedExpr, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(castedExpr), currentLine, currentLineNumber, startCol, endCol);
    this.assertedTypeProvider = assertedTypeProvider;
    this.castedExpr = castedExpr;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // There are actually two cases here. We may need to support static casting where the type of the thing being
    // casted actually can be known at compile-time, but requires a narrowing constraint applied to it (e.g. lambda
    // syntax is too loose for its type to be known by the syntax alone, so its procedure type must be asserted upon
    // it by its surrounding context - and there are rare occasions where this must be done using a cast).
    // Alternatively we may need to support dynamic casting where the type of the thing being casted really truly can't
    // be known until runtime.
    if (castedExpr instanceof CollectionSubscriptExpr
        && (castedExpr.getChildren().get(0) instanceof TupleExpr
            || (castedExpr.getChildren().get(0) instanceof IdentifierReferenceTerm
                && scopedHeap
                    .getValidatedIdentifierType(
                        ((IdentifierReferenceTerm) castedExpr.getChildren().get(0)).identifier)
                    .baseType().equals(BaseType.TUPLE)))) {
      this.isDynamicCast = true;
      // For now, during the type validation phase, The only thing that the CastExpr must assert is that the casted expr
      // in fact currently has an UNDECIDED type.
      // TODO(steving) In the long term, we may need to extend this logic to also handle the case where we're casting
      //  because of some co/contra-variance instead of a compile-time-undecidable type situation.
      this.castedExpr.setAcceptUndecided(true);
      this.castedExpr.assertExpectedBaseType(scopedHeap, BaseType.UNDECIDED);

      // We're trusting the programmer by contract at compile-time. At runtime we'll generate code to check if they were
      // actually right.
      this.actualAssertedType = this.assertedTypeProvider.resolveType(scopedHeap);
    } else {
      // Since this is a static cast situation where the type actually should be knowable in context, we'll actually
      // perform static type validation according to the casted type.
      this.isDynamicCast = false;
      this.actualAssertedType = this.assertedTypeProvider.resolveType(scopedHeap);
      this.castedExpr.assertExpectedExprType(scopedHeap, this.actualAssertedType);
    }

    return this.actualAssertedType;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource castedGenJavaSource = this.castedExpr.generateJavaSourceOutput(scopedHeap);

    if (this.isDynamicCast) {
      StringBuilder resJavaSourceBody = new StringBuilder(
          String.format(
              "ClaroRuntimeUtilities.<%s>assertedTypeValue(%s, %s)",
              this.actualAssertedType.getJavaSourceType(),
              this.actualAssertedType.getJavaSourceClaroType(),
              castedGenJavaSource.javaSourceBody().toString()
          )
      );
      // We've already consumed javaSourceBody, it's safe to clear.
      castedGenJavaSource.javaSourceBody().setLength(0);

      return GeneratedJavaSource.forJavaSourceBody(resJavaSourceBody)
          .createMerged(castedGenJavaSource);
    } else {
      return castedGenJavaSource;
    }
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    Object evaluatedCastedExprValue = this.castedExpr.generateInterpretedOutput(scopedHeap);
    return ClaroRuntimeUtilities.assertedTypeValue(
        assertedTypeProvider.resolveType(scopedHeap), evaluatedCastedExprValue);
  }
}
