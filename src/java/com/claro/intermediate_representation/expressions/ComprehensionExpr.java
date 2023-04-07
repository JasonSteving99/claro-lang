package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class ComprehensionExpr extends Expr {
  private static final ImmutableSet<BaseType> SUPPORTED_COLLECTION_TYPES =
      ImmutableSet.of(BaseType.LIST, BaseType.SET, BaseType.MAP);
  private static long count = 0;

  private final BaseType comprehensionResultBaseType;
  private final Expr mappedItemExpr;
  private final Expr mappedItemKeyExpr;
  private final Expr mappedItemValExpr;
  private final IdentifierReferenceTerm itemName;
  private final Expr collectionExpr;
  private final Optional<Expr> whereClauseExpr;
  private final boolean isMutable;
  private final long uniqueId;
  private Type validatedCollectionExprType;
  private Type validatedComprehensionResultType;
  private Type validatedItemType;
  private Type assertedExprType;

  public ComprehensionExpr(BaseType comprehensionResultBaseType, Expr mappedItemExpr, IdentifierReferenceTerm itemName, Expr collectionExpr, Optional<Expr> whereClauseExpr, boolean isMutable, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.comprehensionResultBaseType = comprehensionResultBaseType;
    this.mappedItemExpr = mappedItemExpr;
    this.mappedItemKeyExpr = null;
    this.mappedItemValExpr = null;
    this.itemName = itemName;
    this.collectionExpr = collectionExpr;
    this.whereClauseExpr = whereClauseExpr;
    this.isMutable = isMutable;
    this.uniqueId = ComprehensionExpr.count++;
  }

  public ComprehensionExpr(BaseType comprehensionResultBaseType, Expr mappedItemKeyExpr, Expr mappedItemValExpr, IdentifierReferenceTerm itemName, Expr collectionExpr, Optional<Expr> whereClauseExpr, boolean isMutable, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.comprehensionResultBaseType = comprehensionResultBaseType;
    this.mappedItemExpr = null;
    this.mappedItemKeyExpr = mappedItemKeyExpr;
    this.mappedItemValExpr = mappedItemValExpr;
    this.itemName = itemName;
    this.collectionExpr = collectionExpr;
    this.whereClauseExpr = whereClauseExpr;
    this.isMutable = isMutable;
    this.uniqueId = ComprehensionExpr.count++;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    this.assertedExprType = expectedExprType;
    super.assertExpectedExprType(scopedHeap, expectedExprType);
  }


  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    boolean mustShadowForTheSakeOfGoodErrorMessagesBecauseItemNameAlreadyDeclared = false;
    if (scopedHeap.isIdentifierDeclared(this.itemName.identifier)) {
      this.itemName.logTypeError(ClaroTypeException.forUnexpectedIdentifierRedeclaration(this.itemName.identifier));
      mustShadowForTheSakeOfGoodErrorMessagesBecauseItemNameAlreadyDeclared = true;
    }

    // First thing, validate the collection expression.
    this.validatedCollectionExprType = this.collectionExpr.getValidatedExprType(scopedHeap);
    if (!ComprehensionExpr.SUPPORTED_COLLECTION_TYPES.contains(this.validatedCollectionExprType.baseType())) {
      this.collectionExpr.logTypeError(
          new ClaroTypeException(this.validatedCollectionExprType, ComprehensionExpr.SUPPORTED_COLLECTION_TYPES));
      // There's really no way forward from here. If the iteratedExpr's type is unsupported then we have an invalid loop.
      // I do still want some level of type-checking the body though, so I'll just choose to set the validatedItemType
      // to UNKNOWABLE so that we have *something* to work with.
      this.validatedItemType = Types.UNKNOWABLE;
    }

    // We need to place the itemName in the symbol table so that the subsequent Exprs can reference it.
    switch (this.validatedCollectionExprType.baseType()) {
      case LIST:
        this.validatedItemType = ((Types.ListType) this.validatedCollectionExprType).getElementType();
        break;
      case SET:
        this.validatedItemType =
            this.validatedCollectionExprType.parameterizedTypeArgs().get(Types.SetType.PARAMETERIZED_TYPE);
        break;
      case MAP:
        // When iterating a Map, by default we're going to assume that you want to iterate the "entries" of the map.
        Type keyType =
            this.validatedCollectionExprType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS);
        Type valueType =
            this.validatedCollectionExprType.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES);
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

    // If there's a where clause we need to type check it.
    if (this.whereClauseExpr.isPresent()) {
      this.whereClauseExpr.get().assertExpectedExprType(scopedHeap, Types.BOOLEAN);
    }

    switch (this.comprehensionResultBaseType) {
      case LIST:
        this.validatedComprehensionResultType =
            Types.ListType.forValueType(
                getValidatedMappedItemType(scopedHeap, BaseType.LIST, l -> ((Types.ListType) l).getElementType(), this.mappedItemExpr),
                this.isMutable
            );
        break;
      case SET:
        this.validatedComprehensionResultType =
            Types.SetType.forValueType(
                getValidatedMappedItemType(
                    scopedHeap, BaseType.SET, s -> s.parameterizedTypeArgs()
                        .get(Types.SetType.PARAMETERIZED_TYPE), this.mappedItemExpr),
                this.isMutable
            );
        break;
      case MAP:
        this.validatedComprehensionResultType =
            Types.MapType.forKeyValueTypes(
                getValidatedMappedItemType(
                    scopedHeap,
                    BaseType.MAP,
                    s -> s.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_KEYS),
                    this.mappedItemKeyExpr
                ),
                getValidatedMappedItemType(
                    scopedHeap,
                    BaseType.MAP,
                    s -> s.parameterizedTypeArgs().get(Types.MapType.PARAMETERIZED_TYPE_VALUES),
                    this.mappedItemValExpr
                ),
                this.isMutable
            );
        break;
      default:
        throw new RuntimeException(
            "Internal Compiler Error! Impossible Comprehension Result Base Type: " + this.comprehensionResultBaseType);
    }

    // Now we're done with the synthetic iterm variable.
    scopedHeap.deleteIdentifierValue(this.itemName.identifier);

    return validatedComprehensionResultType;
  }

  private Type getValidatedMappedItemType(ScopedHeap scopedHeap, BaseType comprehensionBaseType, Function<Type, Type> getElemTypeFn, Expr expr) throws ClaroTypeException {
    Type validatedMappedItemType;
    if (this.assertedExprType == null
        || !this.assertedExprType.baseType().equals(comprehensionBaseType)) {
      // Either we're doing type inference so any mapped item type is acceptable, or the asserted type is definitely
      // not matching this actual comprehension expr, so we'll allow the superclass to handle throwing the type
      // mismatch error reporting.
      validatedMappedItemType = expr.getValidatedExprType(scopedHeap);
    } else {
      validatedMappedItemType = getElemTypeFn.apply(this.assertedExprType);
      expr.assertExpectedExprType(scopedHeap, validatedMappedItemType);
    }
    return validatedMappedItemType;
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Set up a synthetic variable to hold the results of the comprehension as a prefix stmt, but don't initialize it
    // yet as we don't want to run expressions out of order (same reason why I moved away from the `?` operator and
    // moved to the `?=` operator instead, except in this case there's a way to preserve ordering by being careful).
    // Unfortunately, I can't set the size early because I can't evaluate the list early.
    String syntheticCollectionVarName = String.format("$comprehensionCollection_%s", this.uniqueId);
    Stmt.addGeneratedJavaSourceStmtBeforeCurrentStmt(
        String.format(
            "%s %s = new Claro%s(%s);\n",
            this.validatedComprehensionResultType.getJavaSourceType(),
            syntheticCollectionVarName,
            baseTypeToJavaCodegenName(this.comprehensionResultBaseType),
            this.validatedComprehensionResultType.getJavaSourceClaroType()
        ));

//    // Codegen like the following list-comprehension:
//    //   var l = [ x * 2 | x in [1,2,3,99,4] where x < 10];
//
//    ClaroList<Integer> $comprehensionCollection_0 = new ArrayList<>();
//    ClaroList<Integer> l = ((Function) ($streamedCollection) -> {$streamedCollection.stream().filter(x -> x < 10).map(x -> x * 2).forEach($comprehensionCollection_0::add); return $comprehensionCollection;}).apply(Arrays.asList(1,2,3,99,4)));

    // First codegen the collection we're mapping over.
    String syntheticStreamedCollectionVarName = String.format("$streamedCollection_%s", this.uniqueId);
    GeneratedJavaSource res =
        GeneratedJavaSource.forJavaSourceBody(new StringBuilder(syntheticStreamedCollectionVarName));
    // Now start streaming.
    res.javaSourceBody().append(".stream()");
    // From now, everything will depend on the itemName var.
    scopedHeap.putIdentifierValue(this.itemName.identifier, this.validatedItemType);
    scopedHeap.initializeIdentifier(this.itemName.identifier);
    // If it's possible that we have a filter clause to handle.
    if (this.whereClauseExpr.isPresent()) {
      res.javaSourceBody().append(".filter(")
          .append(this.itemName.identifier)
          .append(" -> ");
      res = res.createMerged(this.whereClauseExpr.get().generateJavaSourceOutput(scopedHeap));
      res.javaSourceBody().append(")");
    }
    // Now apply the mapping.
    res.javaSourceBody().append(".map(")
        .append(this.itemName.identifier)
        .append(" -> ");
    if (ImmutableSet.of(BaseType.LIST, BaseType.SET).contains(this.comprehensionResultBaseType)) {
      // If we're mapping to a set or list we just accept the map expr as is.
      res = res.createMerged(this.mappedItemExpr.generateJavaSourceOutput(scopedHeap));
    } else {
      // For a map, we actually need to convert their key value into a ClaroTuple to be added to the map.
      res.javaSourceBody()
          .append("new ClaroTuple(")
          .append(
              Types.TupleType.forValueTypes(
                  ImmutableList.of(
                      this.validatedComprehensionResultType.parameterizedTypeArgs()
                          .get(Types.MapType.PARAMETERIZED_TYPE_KEYS),
                      this.validatedComprehensionResultType.parameterizedTypeArgs()
                          .get(Types.MapType.PARAMETERIZED_TYPE_VALUES)
                  ),
                  /*isMutable=*/false
              ).getJavaSourceClaroType())
          .append(", ");
      res = res.createMerged(this.mappedItemKeyExpr.generateJavaSourceOutput(scopedHeap));
      res.javaSourceBody().append(", ");
      res = res.createMerged(this.mappedItemValExpr.generateJavaSourceOutput(scopedHeap));
      res.javaSourceBody().append(')');
    }
    res.javaSourceBody().append(")");
    // Finally, just need to add each streamed value to the result collection!
    res.javaSourceBody().append(".forEach(");
    if (ImmutableSet.of(BaseType.LIST, BaseType.SET).contains(this.comprehensionResultBaseType)) {
      // If we're mapping to a set or list we just accept the map expr as is.
      res.javaSourceBody()
          .append(syntheticCollectionVarName)
          .append("::add);");
    } else {
      // In the case of collecting to a map, I actually need to unpack the Tuple and put the key/val into the map.
      res.javaSourceBody()
          .append("t -> ")
          .append(syntheticCollectionVarName)
          .append(".put(")
          .append('(')
          .append(this.validatedComprehensionResultType.parameterizedTypeArgs()
                      .get(Types.MapType.PARAMETERIZED_TYPE_KEYS)
                      .getJavaSourceType())
          .append(") t.getElement(0), (")
          .append(this.validatedComprehensionResultType.parameterizedTypeArgs()
                      .get(Types.MapType.PARAMETERIZED_TYPE_VALUES)
                      .getJavaSourceType())
          .append(") t.getElement(1)));");
    }

    // Now we're done with the synthetic iterm variable.
    scopedHeap.deleteIdentifierValue(this.itemName.identifier);

    // Before returning, I just need to be careful to actually do all of this within a `Function` so that after adding
    // to the collection, I can return the collection inline.
    res =
        GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder("((Function<")
                .append(this.validatedCollectionExprType.getJavaSourceType())
                .append(", ")
                .append(this.validatedComprehensionResultType.getJavaSourceType())
                .append(">) (")
                .append(syntheticStreamedCollectionVarName)
                .append(") -> {")
        ).createMerged(res);
    res.javaSourceBody().append(" return ")
        .append(syntheticCollectionVarName)
        .append(";}).apply(");
    res = res.createMerged(this.collectionExpr.generateJavaSourceOutput(scopedHeap));
    res.javaSourceBody().append(")");
    return res;
  }

  private static String baseTypeToJavaCodegenName(BaseType baseType) {
    switch (baseType) {
      case LIST:
        return "List";
      case SET:
        return "Set";
      case MAP:
        return "Map";
      default:
        throw new RuntimeException(
            "Internal Compiler Error! Should've already rejected this unsupported comprehension result type: " +
            baseType);
    }
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Eventually need to impl comprehensions when I come back to adding support for the interpreted backend.
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support comprehensions in the interpreted backend just yet!");
  }
}
