package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.*;
import com.claro.runtime_utilities.ClaroRuntimeUtilities;
import com.google.common.collect.ImmutableList;

import java.util.Optional;
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
    Optional<Types.TupleType> optionalSubscriptedTupleType = Optional.empty();
    if (castedExpr instanceof CollectionSubscriptExpr) {
      if (castedExpr.getChildren().get(0) instanceof TupleExpr) {
        optionalSubscriptedTupleType =
            Optional.of((Types.TupleType) ((TupleExpr) castedExpr.getChildren()
                .get(0)).getValidatedExprType(scopedHeap));
      } else if (castedExpr.getChildren().get(0) instanceof IdentifierReferenceTerm) {
        Type castedType = scopedHeap
            .getValidatedIdentifierType(
                ((IdentifierReferenceTerm) castedExpr.getChildren().get(0)).identifier);
        if (castedType.baseType().equals(BaseType.TUPLE)) {
          optionalSubscriptedTupleType = Optional.of((Types.TupleType) castedType);
        }
      } else if (castedExpr.getChildren().get(0) instanceof UnwrapUserDefinedTypeExpr) {
        Type castedType =
            ((UnwrapUserDefinedTypeExpr) castedExpr.getChildren().get(0)).getValidatedExprType(scopedHeap);
        if (castedType.baseType().equals(BaseType.TUPLE)) {
          optionalSubscriptedTupleType = Optional.of((Types.TupleType) castedType);
        }
      }
    }
    if (castedExpr instanceof CollectionSubscriptExpr && optionalSubscriptedTupleType.isPresent()) {
      // We're trusting the programmer by contract at compile-time. At runtime we'll generate code to check if they were
      // actually right.
      this.actualAssertedType = this.assertedTypeProvider.resolveType(scopedHeap);

      // We're not just gonna accept any arbitrary cast. Only allow casts that may actually be possible at runtime.
      if (!optionalSubscriptedTupleType.get().getValueTypes().contains(this.actualAssertedType)) {
        throw ClaroTypeException.forInvalidCast(this.actualAssertedType, optionalSubscriptedTupleType.get()
            .getValueTypes());
      }

      this.isDynamicCast = true;
      // For now, during the type validation phase, The only thing that the CastExpr must assert is that the casted expr
      // in fact currently has an UNDECIDED type.
      // TODO(steving) In the long term, we may need to extend this logic to also handle the case where we're casting
      //  because of some co/contra-variance instead of a compile-time-undecidable type situation.
      this.castedExpr.setAcceptUndecided(true);
      this.castedExpr.assertExpectedBaseType(scopedHeap, BaseType.UNDECIDED);
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
