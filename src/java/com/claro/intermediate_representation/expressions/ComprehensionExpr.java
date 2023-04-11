package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ComprehensionExpr extends Expr {
  private static final ImmutableSet<BaseType> SUPPORTED_COLLECTION_TYPES =
      ImmutableSet.of(BaseType.LIST, BaseType.SET, BaseType.MAP);
  private static long TOTAL_COMPREHENSIONS_COUNT = 0;

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
  private boolean isOutermostNestedComprehension;
  private boolean requiresNestedCodegenHandling = false;
  private HashSet<String> nestedComprehensionIdentifierReferencesForCodegen;
  private HashSet<String> outermostNestedComprehensionCollectionExprIdentifierRefs = null;

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
    this.uniqueId = ComprehensionExpr.TOTAL_COMPREHENSIONS_COUNT++;
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
    this.uniqueId = ComprehensionExpr.TOTAL_COMPREHENSIONS_COUNT++;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    this.assertedExprType = expectedExprType;
    super.assertExpectedExprType(scopedHeap, expectedExprType);
  }


  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // In order to manage nested comprehensions, track the nesting level each time you enter the type validation here.
    // Importantly, the outermost comprehension needs to track that it's the outermost comprehension in order to be the
    // one to reset the count.
    this.isOutermostNestedComprehension =
        ++InternalStaticStateUtil.ComprehensionExpr_nestedComprehensionCollectionsCount == 0;
    InternalStaticStateUtil.ComprehensionExpr_nestedComprehensionMappedItemName = this.itemName.identifier;

    // There shouldn't be any unhandled exceptions here, but just in case, it's too important that this definitely
    // gets reset to accidentally miss resetting, so just handle in a try-finally just in case.
    try {
      boolean mustShadowForTheSakeOfGoodErrorMessagesBecauseItemNameAlreadyDeclared = false;
      if (scopedHeap.isIdentifierDeclared(this.itemName.identifier)) {
        this.itemName.logTypeError(ClaroTypeException.forUnexpectedIdentifierRedeclaration(this.itemName.identifier));
        mustShadowForTheSakeOfGoodErrorMessagesBecauseItemNameAlreadyDeclared = true;
      }

      // First thing, validate the collection expression.
      this.validatedCollectionExprType = this.collectionExpr.getValidatedExprType(scopedHeap);
      if (this.isOutermostNestedComprehension) {
        // If this is the outermost comprehension, I don't want to automatically require wrapping any collection expr
        // identifier references in a $NestedComprehensionState class at codegen since technically expressions in that
        // one place actually don't fall under any of Java's restrictions regarding effectively-final lambda captures.
        this.outermostNestedComprehensionCollectionExprIdentifierRefs =
            InternalStaticStateUtil.ComprehensionExpr_nestedComprehensionIdentifierReferences;
        InternalStaticStateUtil.ComprehensionExpr_nestedComprehensionIdentifierReferences = new HashSet<>();
      }

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
              "Internal Compiler Error! Impossible Comprehension Result Base Type: " +
              this.comprehensionResultBaseType);
      }

      // Now we're done with the synthetic iterm variable.
      scopedHeap.deleteIdentifierValue(this.itemName.identifier);
    } finally {
      // Finally, to handle nested comprehensions, check the nesting level and the set of nested identifier refs to see
      // if we'll need to do special codegen handling.
      this.requiresNestedCodegenHandling =
          InternalStaticStateUtil.ComprehensionExpr_nestedComprehensionIdentifierReferences.stream()
              .anyMatch(ident -> !ident.startsWith("$") && scopedHeap.isIdentifierDeclared(ident));
      if (this.isOutermostNestedComprehension) {
        InternalStaticStateUtil.ComprehensionExpr_nestedComprehensionCollectionsCount = -1;
        this.nestedComprehensionIdentifierReferencesForCodegen =
            InternalStaticStateUtil.ComprehensionExpr_nestedComprehensionIdentifierReferences.stream()
                .filter(ident -> !ident.startsWith("$") && scopedHeap.isIdentifierDeclared(ident))
                .collect(Collectors.toCollection(HashSet::new));
        InternalStaticStateUtil.ComprehensionExpr_nestedComprehensionIdentifierReferences = new HashSet<>();
      }
    }
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
    // Before doing any codegen of internal Exprs, I need to ensure that any potential IdentifierReferenceTerms are able
    // to defer to the InternalStaticStateUtil to decide whether or not they should codegen to reference var via nested
    // comprehension state.
    if (this.isOutermostNestedComprehension && this.requiresNestedCodegenHandling) {
      // In this case, since I'm going to be wrapping the entire thing in a lambda, then even the outermost
      // comprehension's collection expr needs its identifier refs placed into the $NestedComprehensionState.
      this.nestedComprehensionIdentifierReferencesForCodegen
          .addAll(this.outermostNestedComprehensionCollectionExprIdentifierRefs);
      InternalStaticStateUtil.ComprehensionExpr_nestedComprehensionIdentifierReferences =
          this.nestedComprehensionIdentifierReferencesForCodegen;
    }

    GeneratedJavaSource res =
        GeneratedJavaSource.forJavaSourceBody(new StringBuilder(this.collectionExpr.generateJavaSourceBodyOutput(scopedHeap)));
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
    res.javaSourceBody().append(").collect(");
    // Finally, just need to add each streamed value to the result collection!
    if (this.comprehensionResultBaseType.equals(BaseType.MAP)) {
      // In the case of collecting to a map, I actually need to unpack the Tuple and put the key/val into the map.
      res.javaSourceBody()
          .append("ImmutableMap.toImmutableMap(")
          .append("t -> (")
          .append(this.validatedComprehensionResultType.parameterizedTypeArgs()
                      .get(Types.MapType.PARAMETERIZED_TYPE_KEYS)
                      .getJavaSourceType())
          .append(") t.getElement(0),")
          .append("t -> (")
          .append(this.validatedComprehensionResultType.parameterizedTypeArgs()
                      .get(Types.MapType.PARAMETERIZED_TYPE_VALUES)
                      .getJavaSourceType())
          .append(") t.getElement(1))))");
    } else {
      res.javaSourceBody().append("Collectors.toList()))");
    }

    // Now we're done with the synthetic iterm variable.
    scopedHeap.deleteIdentifierValue(this.itemName.identifier);

    // The entire streamed collection needs to be passed into the corresponding ClaroCollection class.
    res = GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder("new Claro")
                .append(ComprehensionExpr.baseTypeToJavaCodegenName(this.comprehensionResultBaseType))
                .append("(")
                .append(this.validatedComprehensionResultType.getJavaSourceClaroType())
                .append(", "))
        .createMerged(res);

    // Before returning, in the case that there was nesting, I need to be careful to actually do all of this within a
    // `Function` where we pass in the references to potentially non-final internally referenced variables via a hack
    // that works around Java's restriction that all variable references captured by lambdas are effectively-final.
    // We'll do so with a synthetic class that just bundles the state.
    if (this.isOutermostNestedComprehension && this.requiresNestedCodegenHandling) {
      Supplier<Stream<String>> getMappedNestedIdentRefs =
          () ->
              this.nestedComprehensionIdentifierReferencesForCodegen.stream()
                  .map(ident -> String.format(
                      "%s %s",
                      scopedHeap.getValidatedIdentifierType(ident).getJavaSourceType(),
                      ident
                  ));
      String syntheticNestedComprehensionStateClassName = "$NestedComprehensionState_" + this.uniqueId;
      Stmt.addGeneratedJavaSourceStmtBeforeCurrentStmt(
          new StringBuilder("class ")
              .append(syntheticNestedComprehensionStateClassName)
              .append(" {\n\tfinal ")
              .append(getMappedNestedIdentRefs.get().collect(Collectors.joining(";\n\tfinal ", "", ";\n\t")))
              .append(syntheticNestedComprehensionStateClassName)
              .append(getMappedNestedIdentRefs.get().collect(Collectors.joining(", ", "(", ") {\n")))
              .append(
                  this.nestedComprehensionIdentifierReferencesForCodegen.stream()
                      .map(ident -> String.format("\t\tthis.%s = %s;\n", ident, ident))
                      .collect(Collectors.joining()))
              .append("\t}\n}\n")
              .toString()
      );
      res =
          GeneratedJavaSource.forJavaSourceBody(
              new StringBuilder("((Function<")
                  .append(syntheticNestedComprehensionStateClassName)
                  .append(", ")
                  .append(this.validatedComprehensionResultType.getJavaSourceType())
                  .append(">) ($nestedComprehensionState) -> {")
                  .append(this.validatedComprehensionResultType.getJavaSourceType())
                  .append(" $comprehensionResult = ")
          ).createMerged(res);
      res.javaSourceBody()
          .append("; return $comprehensionResult;}).apply(new ")
          .append(syntheticNestedComprehensionStateClassName)
          .append("(")
          .append(String.join(", ", this.nestedComprehensionIdentifierReferencesForCodegen))
          .append("))");

      // Reset InternalStaticStateUtil.
      InternalStaticStateUtil.ComprehensionExpr_nestedComprehensionIdentifierReferences = new HashSet<>();
    }
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
