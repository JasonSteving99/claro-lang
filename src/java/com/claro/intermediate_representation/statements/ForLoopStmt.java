package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class ForLoopStmt extends Stmt {

  private static final ImmutableSet<BaseType> SUPPORTED_COLLECTION_TYPES =
      ImmutableSet.of(BaseType.LIST, BaseType.SET, BaseType.MAP);

  private final IdentifierReferenceTerm itemName;
  private final Expr iteratedExpr;
  private final StmtListNode stmtListNode;
  private BaseType validatedIteratedExprBaseType;
  private Type validatedItemType;

  public ForLoopStmt(IdentifierReferenceTerm itemName, Expr iteratedExpr, StmtListNode stmtListNode) {
    super(ImmutableList.of());
    this.itemName = itemName;
    this.iteratedExpr = iteratedExpr;
    this.stmtListNode = stmtListNode;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // First validate that the itemName isn't attempting to shadow some existing variable.
    boolean mustShadowForTheSakeOfGoodErrorMessagesBecauseItemNameAlreadyDeclared = false;
    if (scopedHeap.isIdentifierDeclared(this.itemName.identifier)) {
      this.itemName.logTypeError(ClaroTypeException.forUnexpectedIdentifierRedeclaration(this.itemName.identifier));
      mustShadowForTheSakeOfGoodErrorMessagesBecauseItemNameAlreadyDeclared = true;
    }

    // TODO(steving) In the future I want to support arbitrary types so long as some `Iterators` contract(s) are impl'd.
    // Then, just validate that the iteratedExpr is actually a collection.
    Type validatedIteratedExprType = this.iteratedExpr.getValidatedExprType(scopedHeap);
    if (!SUPPORTED_COLLECTION_TYPES.contains(validatedIteratedExprType.baseType())) {
      this.iteratedExpr.logTypeError(new ClaroTypeException(validatedIteratedExprType, SUPPORTED_COLLECTION_TYPES));
    }
    this.validatedIteratedExprBaseType = validatedIteratedExprType.baseType();

    // Since we don't know whether the for-loop body will actually execute (since the iteratedExpr might be an empty
    // collection), we won't be able to trigger branch inspection on var initialization.
    scopedHeap.observeNewScope(false);

    // We need to place the itemName in the symbol table so that the stmtListNode can reference it.
    switch (this.validatedIteratedExprBaseType) {
      case LIST:
        this.validatedItemType = ((Types.ListType) validatedIteratedExprType).getElementType();
        break;
      case SET:
        this.validatedItemType =
            validatedIteratedExprType.parameterizedTypeArgs().get(Types.SetType.PARAMETERIZED_TYPE);
        break;
      case MAP:
        // When iterating a Map, by default we're going to assume that you want to iterate the "entries" of the map.
        Type keyType = validatedIteratedExprType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS);
        Type valueType = validatedIteratedExprType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES);
        this.validatedItemType =
            Types.TupleType.forValueTypes(ImmutableList.of(keyType, valueType), /*isMutable=*/false);
        break;
      default:
        // There's really no way forward from here. If the iteratedExpr's type is unsupported then we have an invalid loop.
        // I do still want some level of type-checking the body though, so I'll just choose to set the validatedItemType
        // to UNKNOWABLE so that we have *something* to work with.
        this.validatedItemType = Types.UNKNOWABLE;
    }
    if (mustShadowForTheSakeOfGoodErrorMessagesBecauseItemNameAlreadyDeclared) {
      scopedHeap.putIdentifierValueAllowingHiding(this.itemName.identifier, this.validatedItemType, null);
    } else {
      scopedHeap.putIdentifierValue(this.itemName.identifier, this.validatedItemType);
    }
    scopedHeap.initializeIdentifier(this.itemName.identifier);

    boolean original_withinLoopingConstructBody = InternalStaticStateUtil.LoopingConstructs_withinLoopingConstructBody;
    InternalStaticStateUtil.LoopingConstructs_withinLoopingConstructBody = true;

    // Finally validate the body.
    this.stmtListNode.assertExpectedExprTypes(scopedHeap);

    InternalStaticStateUtil.LoopingConstructs_withinLoopingConstructBody = original_withinLoopingConstructBody;
    scopedHeap.exitCurrObservedScope(false);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Iterated Expr.
    GeneratedJavaSource iteratedExprJavaSource = this.iteratedExpr.generateJavaSourceOutput(scopedHeap);

    // Body.
    scopedHeap.enterNewScope();
    scopedHeap.putIdentifierValue(this.itemName.identifier, this.validatedItemType);
    scopedHeap.initializeIdentifier(this.itemName.identifier);
    GeneratedJavaSource bodyStmtListJavaSource = this.stmtListNode.generateJavaSourceOutput(scopedHeap);
    scopedHeap.exitCurrScope();

    GeneratedJavaSource resGenJavaSource =
        bodyStmtListJavaSource.withNewJavaSourceBody(
            new StringBuilder(
                String.format(
                    "for (%s %s : %s) {\n%s\n}\n",
                    this.validatedItemType.getJavaSourceType(),
                    this.itemName.identifier,
                    iteratedExprJavaSource.javaSourceBody().toString(),
                    bodyStmtListJavaSource.javaSourceBody().toString()
                ))
        );
    iteratedExprJavaSource.javaSourceBody().setLength(0);
    bodyStmtListJavaSource.javaSourceBody().setLength(0);

    return resGenJavaSource.createMerged(iteratedExprJavaSource).createMerged(bodyStmtListJavaSource);
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Eventually need to impl for-loops when I come back to adding support for the interpreted backend.
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support for-loops in the interpreted backend just yet!");
  }
}
