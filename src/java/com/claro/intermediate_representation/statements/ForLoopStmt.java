package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
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
    if (scopedHeap.isIdentifierDeclared(this.itemName.identifier)) {
      this.itemName.logTypeError(ClaroTypeException.forUnexpectedIdentifierRedeclaration(this.itemName.identifier));
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
        throw new RuntimeException(
            "Internal Compiler Error! Should've already rejected for-loop over iteratedExpr type: " +
            validatedIteratedExprType);
    }
    scopedHeap.putIdentifierValue(this.itemName.identifier, this.validatedItemType);
    scopedHeap.initializeIdentifier(this.itemName.identifier);

    // Finally validate the body.
    this.stmtListNode.assertExpectedExprTypes(scopedHeap);
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
    // In order to make sure that the scopes are honored correctly but also that we don't
    // have to wastefully push and pop the same body scope repeatedly, we'll first check
    // the while loop's condition before the new scope is pushed and then do the rest of
    // the iterating inside a do-while after pushing a new scope. This simply allows us to
    // make sure that the loop condition isn't somehow attempting to depend on variables
    // declared only in the body, while also eliminating wasteful pushes/pops of scope stack
    // since on first pass through the body we'll have already checked that all the new vars
    // are actually initialized before access, therefore we don't need to clear the while body's
    // scope, since everything will be reinitialized by the code itself.
    Expr whileCondExpr = (Expr) getChildren().get(0);
    Object maybeReturnValue = null;
    if ((boolean) whileCondExpr.generateInterpretedOutput(scopedHeap)) {
      scopedHeap.enterNewScope();
      do {
        maybeReturnValue = getChildren().get(1).generateInterpretedOutput(scopedHeap);

        if (maybeReturnValue != null) {
          // The last executed Stmt happened to be a ReturnStmt. We therefore need to break out
          // of this loop so that no more potential side effects happen when they shouldn't.
          break;
        }
      } while ((boolean) whileCondExpr.generateInterpretedOutput(scopedHeap));
      scopedHeap.exitCurrScope();
    }
    // This return value is probably `null` unless the last executed Stmt happened to be a ReturnStmt.
    return maybeReturnValue;
  }
}
