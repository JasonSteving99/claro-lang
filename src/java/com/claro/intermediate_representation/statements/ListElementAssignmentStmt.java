package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.CollectionSubscriptExpr;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.term.IntegerTerm;
import com.claro.intermediate_representation.types.*;
import com.claro.intermediate_representation.types.impls.builtins_impls.collections.ClaroList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Optional;

public class ListElementAssignmentStmt extends Stmt {

  private static final ImmutableSet<BaseType> SUPPORTED_EXPR_BASE_TYPES =
      ImmutableSet.of(
          BaseType.LIST,
          BaseType.TUPLE,
          BaseType.MAP
      );
  private final boolean errorProp;
  private Optional<AutomaticErrorPropagationStmt> optionalAutomaticErrorPropagationStmt = Optional.empty();

  public ListElementAssignmentStmt(CollectionSubscriptExpr collectionSubscriptExpr, Expr e) {
    super(
        ImmutableList.of(
            /*listExpr=*/
            collectionSubscriptExpr.getChildren().get(0),
            /*subscriptExpr=*/
            collectionSubscriptExpr.getChildren().get(1),
            /*assignedValueExpr=*/
            e
        )
    );
    this.errorProp = false;
  }

  public ListElementAssignmentStmt(CollectionSubscriptExpr collectionSubscriptExpr, Expr e, boolean errorProp) {
    super(
        ImmutableList.of(
            /*listExpr=*/
            collectionSubscriptExpr.getChildren().get(0),
            /*subscriptExpr=*/
            collectionSubscriptExpr.getChildren().get(1),
            /*assignedValueExpr=*/
            e
        )
    );
    this.errorProp = errorProp;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // First thing first, we need to actually validate that we're correctly referencing a collection type.
    Expr listExpr = (Expr) this.getChildren().get(0);
    Type listExprType = listExpr.getValidatedExprType(scopedHeap);
    if (!SUPPORTED_EXPR_BASE_TYPES.contains(listExprType.baseType())) {
      // Make sure that this mismatch is logged on the offending Expr that was supposed to be a collection.
      listExpr.assertSupportedExprBaseType(scopedHeap, SUPPORTED_EXPR_BASE_TYPES);
      ((Expr) this.getChildren().get(1)).logTypeError(
          ClaroTypeException.forInvalidSubscriptForNonCollectionType(listExprType, SUPPORTED_EXPR_BASE_TYPES));

      // We can't do any more type checking of the rhs because the entire premise of this assignment statement is invalid.
      // However, just in case we need to mark things used, let's run validation on the RHS...not perfect but helpful.
      ((Expr) this.getChildren().get(1)).getValidatedExprType(scopedHeap);
      ((Expr) this.getChildren().get(2)).getValidatedExprType(scopedHeap);
      return;
    }
    if (!((SupportsMutableVariant<?>) listExprType).isMutable()) {
      // Make sure that this collection *actually* supports mutation.
      listExpr.logTypeError(
          ClaroTypeException.forIllegalMutationAttemptOnImmutableValue(
              listExprType, ((SupportsMutableVariant<?>) listExprType).toShallowlyMutableVariant()));
      // The entire premise of this assignment statement is invalid. However, just in case we need to mark things used,
      // let's run validation on the subscript expr and RHS...not perfect but helpful.
    }

    // Type check the index and rhs exprs.
    if (listExprType.baseType().equals(BaseType.MAP)) {
      ((Expr) this.getChildren().get(1)).assertExpectedExprType(
          scopedHeap, listExprType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS));
      if (!this.errorProp) {
        ((Expr) this.getChildren().get(2)).assertExpectedExprType(
            scopedHeap,
            listExprType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES)
        );
      } else {
        this.optionalAutomaticErrorPropagationStmt =
            Optional.of(
                new AutomaticErrorPropagationStmt(
                    Optional.of(listExprType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES)),
                    (Expr) this.getChildren().get(2)
                ));
        this.optionalAutomaticErrorPropagationStmt.get().getValidatedExprType(scopedHeap);
      }
    } else if (listExprType.baseType().equals(BaseType.TUPLE)) {
      Expr subscriptExpr = (Expr) this.getChildren().get(1);
      if (!(subscriptExpr instanceof IntegerTerm)) { // Tuples can only be subscripted for re-assignment using a literal.
        subscriptExpr.logTypeError(ClaroTypeException.forTupleIndexNonLiteralForAssignment());
      } else {
        // We know we have a compile-time constant integer literal, so I'm going to bounds check for the user now.
        int subscriptValue = ((IntegerTerm) subscriptExpr).value;
        ImmutableList<Type> tupleTypes = ((Types.TupleType) listExprType).getValueTypes();
        if (subscriptValue >= tupleTypes.size() || subscriptValue < 0) {
          subscriptExpr.logTypeError(
              ClaroTypeException.forTupleIndexOutOfBounds(listExprType, tupleTypes.size(), subscriptValue));
        } else {
          if (!this.errorProp) {
            ((Expr) this.getChildren().get(2)).assertExpectedExprType(
                scopedHeap,
                tupleTypes.get(subscriptValue)
            );
          } else {
            this.optionalAutomaticErrorPropagationStmt =
                Optional.of(
                    new AutomaticErrorPropagationStmt(
                        Optional.of(tupleTypes.get(subscriptValue)),
                        (Expr) this.getChildren().get(2)
                    ));
            this.optionalAutomaticErrorPropagationStmt.get().getValidatedExprType(scopedHeap);
          }
        }
      }
    } else {
      ((Expr) this.getChildren().get(1)).assertExpectedExprType(scopedHeap, Types.INTEGER);
      Type listElementType = ((Types.Collection) listExprType).getElementType();
      if (!this.errorProp) {
        if (listElementType.baseType().equals(BaseType.ONEOF)) {
          // Since this is assignment to a oneof type, by definition we'll allow any of the type variants supported
          // by this particular oneof instance.
          ((Expr) this.getChildren().get(2)).assertSupportedExprType(
              scopedHeap,
              ImmutableSet.<Type>builder().addAll(((Types.OneofType) listElementType).getVariantTypes())
                  .add(listElementType)
                  .build()
          );
        } else {
          ((Expr) this.getChildren().get(2)).assertExpectedExprType(scopedHeap, listElementType);
        }
      } else {
        this.optionalAutomaticErrorPropagationStmt =
            Optional.of(
                new AutomaticErrorPropagationStmt(
                    Optional.of(listElementType),
                    (Expr) this.getChildren().get(2)
                ));
        this.optionalAutomaticErrorPropagationStmt.get().getValidatedExprType(scopedHeap);
      }
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource genJavaSource0 = this.getChildren().get(0).generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource genJavaSource1 = this.getChildren().get(1).generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource genJavaSource2;
    if (!this.errorProp) {
      genJavaSource2 = this.getChildren().get(2).generateJavaSourceOutput(scopedHeap);
    } else {
      genJavaSource2 = this.optionalAutomaticErrorPropagationStmt.get().generateJavaSourceOutput(scopedHeap);
    }

    GeneratedJavaSource resGenJavaSource = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                "%s.set(%s, %s);\n",
                genJavaSource0.javaSourceBody().toString(),
                genJavaSource1.javaSourceBody().toString(),
                genJavaSource2.javaSourceBody().toString()
            )
        )
    );
    // We've already consumed javaSourceBodyStmt, it's safe to clear.
    genJavaSource0.javaSourceBody().setLength(0);
    genJavaSource1.javaSourceBody().setLength(0);
    genJavaSource2.javaSourceBody().setLength(0);

    return resGenJavaSource.createMerged(genJavaSource0).createMerged(genJavaSource1).createMerged(genJavaSource2);
  }

  // Type info is lost on generateInterpretedOutput, but we know Claro only allows subscript assignment on lists.
  @SuppressWarnings("unchecked")
  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    if (!this.errorProp) {
      // Put the computed value of this specified List element directly into ArrayList in the heap.
      ((ClaroList<Object>) this.getChildren().get(0).generateInterpretedOutput(scopedHeap))
          .set(
              (int) this.getChildren().get(1).generateInterpretedOutput(scopedHeap),
              this.getChildren().get(2).generateInterpretedOutput(scopedHeap)
          );
    } else {
      // TODO(steving) Will need to support early returns here once I get back to supporting the interpreted backend.
      throw new RuntimeException("Internal Compiler Error: Automatic Error Propagating collection element assignment stmts are not yet supported in the interpreted backend!");
    }
    return null;
  }
}
