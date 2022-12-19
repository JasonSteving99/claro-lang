package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IntegerTerm;
import com.claro.intermediate_representation.types.*;
import com.claro.intermediate_representation.types.impls.builtins_impls.collections.Collection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Optional;
import java.util.function.Supplier;

public class CollectionSubscriptExpr extends Expr {

  private Type collectionExprType;
  private Optional<Type> javaSourceNeedsCastBecauseItDoesNotUnderstandClaroTypeInference = Optional.empty();

  private static final ImmutableSet<BaseType> SUPPORTED_EXPR_BASE_TYPES =
      ImmutableSet.of(
          BaseType.LIST,
          BaseType.TUPLE,
          BaseType.STRING,
          BaseType.MAP
      );

  public CollectionSubscriptExpr(Expr collectionNodeExpr, Expr expr, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(collectionNodeExpr, expr), currentLine, currentLineNumber, startCol, endCol);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    Expr collectionExpr = (Expr) this.getChildren().get(0);
    this.collectionExprType = collectionExpr.getValidatedExprType(scopedHeap);
    if (!SUPPORTED_EXPR_BASE_TYPES.contains(this.collectionExprType.baseType())) {
      // Make sure that this mismatch is logged on the offending Expr that was supposed to be a collection.
      collectionExpr.assertSupportedExprBaseType(scopedHeap, SUPPORTED_EXPR_BASE_TYPES);
      throw ClaroTypeException.forInvalidSubscriptForNonCollectionType(this.collectionExprType, SUPPORTED_EXPR_BASE_TYPES);
    }

    // Check that the subscript expr is a valid type.
    if (this.collectionExprType.baseType().equals(BaseType.MAP)) {
      ((Expr) this.getChildren().get(1)).assertExpectedExprType(
          scopedHeap,
          this.collectionExprType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS)
      );
    } else {
      ((Expr) this.getChildren().get(1)).assertExpectedExprType(scopedHeap, Types.INTEGER);
    }

    Type type;

    if (this.collectionExprType.baseType().equals(BaseType.STRING)) {
      type = Types.STRING;
    } else if (this.collectionExprType.baseType().equals(BaseType.MAP)) {
      // TODO(steving) This should be modelled as a oneof<T|KeyErr> or something like that once the type system
      //  is mature enough for that. In this way, the return type would have to be checked with something like:
      //  var res = m["foo"];
      //  match(type(res)) {
      //    case string:
      //      print("Found what I was looking for: {res}");
      //    case KeyErr: # Where KeyErr is defined as an enum w/ multiple vals like KeyErr::Missing inside it.
      //      panic("Expected key wasn't found! Error: {res}");
      //  }
      type = this.collectionExprType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES);
    } else if (this.collectionExprType.baseType().equals(BaseType.TUPLE)
               && (this.getChildren().get(1) instanceof IntegerTerm)) {
      // I can cleanly handle the case of subscripting into a Tuple with an integer literal.
      int literalIndex = ((IntegerTerm) this.getChildren().get(1)).value;
      int tupleActualSize = ((Types.TupleType) this.collectionExprType).getValueTypes().size();
      if (literalIndex >= tupleActualSize || literalIndex < 0) {
        // In this case the index literal was OOB.
        throw ClaroTypeException.forTupleIndexOutOfBounds(this.collectionExprType, tupleActualSize, literalIndex);
      }
      // The user's literal subscript can be trusted, and this is the type they're referencing! This is exciting because
      // it's still type safe, and it's going to amount to a significant reduction in code complexity, to eliminate a
      // bunch of unnecessary type casts.
      type = ((Types.TupleType) this.collectionExprType).getValueTypes().get(literalIndex);
      this.javaSourceNeedsCastBecauseItDoesNotUnderstandClaroTypeInference = Optional.of(type);
    } else {
      type = ((Types.Collection) this.collectionExprType).getElementType();
    }

    if (!this.acceptUndecided) {
      if (type.baseType().equals(BaseType.UNDECIDED)) {
        // We shouldn't be able to reach this because programmers should instead cast undecided values.
        throw ClaroTypeException.forUndecidedTypeLeak();
      }
    }

    Type res = TypeProvider.Util.maybeDereferenceAliasSelfReference(type, scopedHeap);
    if (!res.equals(type)) {
      this.javaSourceNeedsCastBecauseItDoesNotUnderstandClaroTypeInference = Optional.of(res);
    }
    return res;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource exprGenJavaSource0 = getChildren().get(0).generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource exprGenJavaSource1 = getChildren().get(1).generateJavaSourceOutput(scopedHeap);

    GeneratedJavaSource subscriptExprGenJavaSource;
    if (this.collectionExprType.baseType().equals(BaseType.STRING)) {
      subscriptExprGenJavaSource =
          GeneratedJavaSource.forJavaSourceBody(new StringBuilder("Character.toString("))
              .createMerged(
                  exprGenJavaSource0.createMerged(
                      GeneratedJavaSource.forJavaSourceBody(
                          new StringBuilder(
                              String.format(
                                  ".charAt(%s))",
                                  exprGenJavaSource1.javaSourceBody().toString()
                              ))))
              );
    } else {
      subscriptExprGenJavaSource =
          exprGenJavaSource0.createMerged(
              GeneratedJavaSource.forJavaSourceBody(
                  new StringBuilder(
                      String.format(
                          ".getElement(%s)",
                          exprGenJavaSource1.javaSourceBody().toString()
                      ))));

      if (this.javaSourceNeedsCastBecauseItDoesNotUnderstandClaroTypeInference.isPresent()) {
        // In this case, just based on the API to the ClaroTuple implementation, Java has no idea what the type is even
        // though now in this case Claro does know this type, so I may need a cast.
        subscriptExprGenJavaSource = GeneratedJavaSource
            .forJavaSourceBody(
                new StringBuilder(
                    String.format(
                        "((%s) ",
                        this.javaSourceNeedsCastBecauseItDoesNotUnderstandClaroTypeInference.get().getJavaSourceType()
                    )))
            .createMerged(subscriptExprGenJavaSource)
            .createMerged(GeneratedJavaSource.forJavaSourceBody(new StringBuilder(")")));
      }
    }

    // We've already consumed the javaSourceBody, we're safe to clear it.
    exprGenJavaSource1.javaSourceBody().setLength(0);

    return subscriptExprGenJavaSource.createMerged(exprGenJavaSource1);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    if (this.collectionExprType.baseType().equals(BaseType.STRING)) {
      int index = (int) getChildren().get(1).generateInterpretedOutput(scopedHeap);
      return ((String) getChildren().get(0).generateInterpretedOutput(scopedHeap))
          .substring(index, index + 1);
    }
    return ((Collection) getChildren().get(0).generateInterpretedOutput(scopedHeap))
        .getElement((int) getChildren().get(1).generateInterpretedOutput(scopedHeap));
  }
}
