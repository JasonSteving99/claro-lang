package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.UnwrapUserDefinedTypeExpr;
import com.claro.intermediate_representation.expressions.procedures.functions.FunctionCallExpr;
import com.claro.intermediate_representation.expressions.procedures.functions.StructuralConcreteGenericTypeValidationUtil;
import com.claro.intermediate_representation.expressions.term.*;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MatchStmt extends Stmt {
  private static long globalMatchCount = 0;
  private Expr matchedExpr;
  private ImmutableList<ImmutableList<Object>> cases;
  private final long matchId;
  private ImmutableList caseExprLiterals;
  private Type matchedExprType;
  private Optional<UnwrapUserDefinedTypeExpr> optionalUnwrapMatchedExpr = Optional.empty();
  private Optional<String> optionalWildcardBindingName = Optional.empty();
  private Optional<ImmutableList<Type>> optionalFlattenedMatchedValues = Optional.empty();

  public MatchStmt(Expr matchedExpr, ImmutableList<ImmutableList<Object>> cases) {
    super(ImmutableList.of());
    this.matchedExpr = matchedExpr;
    this.cases = cases;
    this.matchId = MatchStmt.globalMatchCount++;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // TODO(steving) In the future should be able to match over more than just primitives. (Probably everything other
    //   than future<T> since there's no value to match yet?)
    Type matchedExprType = this.matchedExpr.getValidatedExprType(scopedHeap);
    // All base values must be of some supported primitive type (int/string/boolean).
    this.optionalFlattenedMatchedValues = validateMatchedExprTypeIsSupported(matchedExprType, scopedHeap);

    ImmutableList.Builder<String> caseExprLiterals = ImmutableList.builder();
    AtomicReference<Optional<HashSet<Type>>> foundTypeLiterals = new AtomicReference<>(Optional.empty());
    Function<Object, Boolean> checkUniqueness =
        getCheckUniquenessFn(scopedHeap, matchedExprType, caseExprLiterals, foundTypeLiterals);
    // Make sure that the cases get preprocessed (flattened) if they're actually nested.
    if (ImmutableSet.of(BaseType.STRUCT, BaseType.TUPLE).contains(matchedExprType.baseType())) {
      this.cases = flattenNestedPatterns(this.cases, matchedExprType, scopedHeap);
    }
    // Need to ensure that at the very least our cases look reasonable.
    AtomicBoolean foundDefaultCase = new AtomicBoolean(false);
    for (int i = 0; i < this.cases.size(); i++) {
      ImmutableList<Object> currCase;
      currCase = this.cases.get(i);
      AtomicBoolean narrowingRequired = new AtomicBoolean(false);
      // TODO(steving) TESTING!! This is pretty freakin ugly. Should model actual types for these separate situations.
      if (currCase.get(0) instanceof ImmutableList) {
        for (Object currCasePattern : (ImmutableList<Object>) currCase.get(0)) {
          validateCasePattern(scopedHeap, matchedExprType, checkUniqueness, foundDefaultCase, currCasePattern, narrowingRequired);
        }
      } else {
        validateCasePattern(scopedHeap, matchedExprType, checkUniqueness, foundDefaultCase, currCase.get(0), narrowingRequired);
      }

      // Each branch gets isolated to its own scope.
      scopedHeap.observeNewScope(/*beginIdentifierInitializationBranchInspection=*/true);
      // Handle narrowing if required.
      Optional<Type> originalIdentifierTypeMarkedNarrowed = Optional.empty();
      if (narrowingRequired.get()) {
        // First need to mark the identifier's type as having been narrowed so that any references to it know that
        // they should instead be referencing the synthetic narrowed identifier. This will be reset after.
        String identifierName = ((IdentifierReferenceTerm) this.matchedExpr).identifier;
        originalIdentifierTypeMarkedNarrowed = Optional.of(scopedHeap.getValidatedIdentifierType(identifierName));
        originalIdentifierTypeMarkedNarrowed.get().autoValueIgnored_IsNarrowedType.set(true);

        String narrowedTypeSyntheticIdentifier = String.format("$NARROWED_%s", identifierName);
        scopedHeap.putIdentifierValueAllowingHiding(
            narrowedTypeSyntheticIdentifier, ((TypeProviderPattern) currCase.get(0)).toImpliedType(scopedHeap), null);
        scopedHeap.markIdentifierUsed(narrowedTypeSyntheticIdentifier);
      }

      // Type check the stmt-list associated with this case.
      ((StmtListNode) currCase.get(1)).assertExpectedExprTypes(scopedHeap);

      // Undo narrowing if necessary.
      if (narrowingRequired.get()) {
        originalIdentifierTypeMarkedNarrowed.get().autoValueIgnored_IsNarrowedType.set(false);
      }
      // After observing all scopes in this exhaustive match, then finalize the branch inspection.
      scopedHeap.exitCurrObservedScope(/*finalizeIdentifierInitializationBranchInspection=*/i == this.cases.size() - 1);

      // Drop any wildcard bindings if necessary.
      {
        Object wildcardBindingIdentifier;
        if (currCase.get(0) instanceof UserDefinedTypePattern
            && (wildcardBindingIdentifier =
                    ((UserDefinedTypePattern) currCase.get(0)).getWrappedValue()) instanceof IdentifierReferenceTerm) {
          scopedHeap.deleteIdentifierValue(((IdentifierReferenceTerm) wildcardBindingIdentifier).identifier);
        }
      }
    }

    if (matchedExprType.baseType().equals(BaseType.BOOLEAN)) {
      if (!foundDefaultCase.get() && this.cases.size() < 2) {
        // Ugly hack means that errors for duplicate branches and exhaustiveness will show up on separate compiles...not sure if I like this.
        this.matchedExpr.logTypeError(ClaroTypeException.forMatchIsNotExhaustiveOverAllPossibleValues(matchedExprType));
      }
      // It's useless to have a default case when the other branches are already exhaustively matching the value.
      if (foundDefaultCase.get() && this.cases.size() > 2) {
        // TODO(steving) This is lazy, I should be able to log this error on the actual offending `_`.
        this.matchedExpr.logTypeError(ClaroTypeException.forUselessDefaultCaseInAlreadyExhaustiveMatch());
      }
    } else if (!foundDefaultCase.get() &&
               !(foundTypeLiterals.get().isPresent()
                 && foundTypeLiterals.get().get().containsAll(((Types.OneofType) matchedExprType).getVariantTypes()))) {
      // TODO(steving) This is an ugly error, it shouldn't point at the matched expr, it should point at the `match` keyword.
      this.matchedExpr.logTypeError(ClaroTypeException.forMatchIsNotExhaustiveOverAllPossibleValues(matchedExprType));
    }

    // It's useless to use a match statement to just simply match a wildcard.
    if (foundDefaultCase.get() && this.cases.size() < 2) {
      // TODO(steving) This is an ugly error, it shouldn't point at the matched expr, it should point at the `match` keyword.
      this.matchedExpr.logTypeError(ClaroTypeException.forUselessMatchStatementOverSingleDefaultCase());
    }
    // It's useless to have a default case when the other branches are already exhaustively matching the value.
    if (matchedExprType.baseType().equals(BaseType.ONEOF)
        && foundDefaultCase.get()
        && foundTypeLiterals.get().isPresent()
        && foundTypeLiterals.get().get().containsAll(((Types.OneofType) matchedExprType).getVariantTypes())) {
      // TODO(steving) This is lazy, I should be able to log this error on the actual offending `_`.
      this.matchedExpr.logTypeError(ClaroTypeException.forUselessDefaultCaseInAlreadyExhaustiveMatch());
    }

    // Preserve information needed for codegen.
    this.caseExprLiterals = caseExprLiterals.build();
    this.matchedExprType = matchedExprType;
  }

  // Here, we'll recursively descend into the type of the matched expr to determine that pattern matching over the type
  // is actually supported. For now, only int/string/boolean/user-defined-type-of-prior-types/nested-tuples-and-structs-of-prior-types
  // are supported.
  // Returns: the type sequence that the flattened patterns will effectively codegen against.
  private Optional<ImmutableList<Type>> validateMatchedExprTypeIsSupported(Type matchedExprType, ScopedHeap scopedHeap) throws ClaroTypeException {
    ImmutableList.Builder<Type> res = ImmutableList.builder();
    ImmutableSet.Builder<Type> invalidTypesFound = ImmutableSet.builder();
    if (validateMatchedExprTypeIsSupported(matchedExprType, scopedHeap, res, invalidTypesFound)) {
      return Optional.of(res.build());
    }
    this.matchedExpr.logTypeError(
        ClaroTypeException.forMatchOverUnsupportedBaseValueTypes(matchedExprType, invalidTypesFound.build()));
    return Optional.empty();
  }

  private boolean validateMatchedExprTypeIsSupported(
      Type matchedExprType, ScopedHeap scopedHeap, ImmutableList.Builder<Type> flattenedMatchValueTypes, ImmutableSet.Builder<Type> invalidTypesFound)
      throws ClaroTypeException {
    switch (matchedExprType.baseType()) {
      case INTEGER:
      case STRING:
      case BOOLEAN:
        flattenedMatchValueTypes.add(matchedExprType);
        return true; // Supported!
      case USER_DEFINED_TYPE:
        // Don't want to duplicate the unwrap validation logic, so I'll defer to the Expr node impl.
        if (!UnwrapUserDefinedTypeExpr.validateUnwrapIsLegal((Types.UserDefinedType) matchedExprType)) {
          this.matchedExpr.logTypeError(ClaroTypeException.forIllegalUseOfUserDefinedTypeDefaultUnwrapperOutsideOfUnwrapperProcedures(
              matchedExprType,
              InternalStaticStateUtil.UnwrappersBlockStmt_unwrappersByUnwrappedType.get(((Types.UserDefinedType) matchedExprType).getTypeName())
          ));
          invalidTypesFound.add(matchedExprType);
          return false;
        }

        // Make sure it's actually legal to unwrap this user defined type.
        int errorsBefore = Expr.typeErrorsFound.size();
        Type wrappedType =
            new UnwrapUserDefinedTypeExpr(
                new Expr(ImmutableList.of(), this.matchedExpr.currentLine, this.matchedExpr.currentLineNumber, this.matchedExpr.startCol, this.matchedExpr.endCol) {
                  @Override
                  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
                    return matchedExprType;
                  }

                  @Override
                  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
                    throw new RuntimeException("Internal Compiler Error! Should be unreachable.");
                  }
                },
                this.matchedExpr.currentLine, this.matchedExpr.currentLineNumber, this.matchedExpr.startCol, this.matchedExpr.endCol
            ).getValidatedExprType(scopedHeap);

        if (Expr.typeErrorsFound.size() > errorsBefore) {
          // Apparently we've run into some error unwrapping this value, can't continue.
          invalidTypesFound.add(matchedExprType);
          return false;
        }

        // Now I just need to ensure that the wrapped type is something supported.
        return validateMatchedExprTypeIsSupported(wrappedType, scopedHeap, flattenedMatchValueTypes, invalidTypesFound);
      case ONEOF:
        ImmutableList.Builder<Type> variants = ImmutableList.builder();
        for (Type variantType : ((Types.OneofType) matchedExprType).getVariantTypes()) {
          // Here we actually don't want to add each variant to the flattenedMatchValueTypes list as these types are all
          // represented in a single match "slot"...
          if (!validateMatchedExprTypeIsSupported(variantType, scopedHeap, variants, invalidTypesFound)) {
            return false;
          }
        }
        // The oneof itself gets placed into the flattenedMatchValueTypes for this pattern "slot".
        flattenedMatchValueTypes.add(matchedExprType);
        return true;
      case TUPLE:
        for (Type elemType : ((Types.TupleType) matchedExprType).getValueTypes()) {
          if (!validateMatchedExprTypeIsSupported(elemType, scopedHeap, flattenedMatchValueTypes, invalidTypesFound)) {
            return false;
          }
        }
        return true;
      case STRUCT:
        for (Type elemType : ((Types.StructType) matchedExprType).getFieldTypes()) {
          if (!validateMatchedExprTypeIsSupported(elemType, scopedHeap, flattenedMatchValueTypes, invalidTypesFound)) {
            return false;
          }
        }
        return true;
      default:
        invalidTypesFound.add(matchedExprType);
        return false;
    }
  }

  private void validateCasePattern(ScopedHeap scopedHeap, Type matchedExprType, Function<Object, Boolean> checkUniqueness, AtomicBoolean foundDefaultCase, Object currCasePattern, AtomicBoolean narrowingRequired) throws ClaroTypeException {
    if (currCasePattern instanceof Expr) {
      Expr currCaseExpr = (Expr) currCasePattern;
      validateCaseExpr(scopedHeap, matchedExprType, checkUniqueness, currCaseExpr);
    } else if (currCasePattern instanceof TypeProviderPattern) {
      // Ensure that each literal value is unique.
      if (!checkUniqueness.apply(currCasePattern)) {
        // TODO(steving) This is lazy, I should be able to log this error on the actual offending type literal.
        this.matchedExpr.logTypeError(ClaroTypeException.forDuplicateMatchCase());
      }
      if (!matchedExprType.baseType().equals(BaseType.ONEOF)) {
        // I'm not going to allow type literals as patterns for non-oneof values as it's a useless match.
        this.matchedExpr.logTypeError(ClaroTypeException.forUselessMatchCaseTypeLiteralPatternForNonOneofMatchedVal(matchedExprType));
      }
      // Handle narrowing if possible.
      if (this.matchedExpr instanceof IdentifierReferenceTerm) {
        narrowingRequired.set(true);
      }
    } else if (currCasePattern instanceof UserDefinedTypePattern) {
      UserDefinedTypePattern pattern = (UserDefinedTypePattern) currCasePattern;
      try {
        // Validate that the pattern's User-Defined type is actually a user-defined type.
        if (!TypeProvider.Util.getTypeByName(pattern.getTypeName().identifier, /*isTypeDefinition=*/true)
            .resolveType(scopedHeap)
            .baseType()
            .equals(BaseType.USER_DEFINED_TYPE)) {
          throw ClaroTypeException.forIllegalProcedureCallInMatchCasePattern();
        }
      } catch (Exception e) { // Correctly attribute any lookup errors.
        pattern.getTypeName().logTypeError(e);
      }

      // TODO(steving) TESTING!!! This handling of wildcards is no longer sufficiently general. It only works on
      //    patterns like `case Foo(X) -> ...use X...;` whereas it should also work on something less trivial like
      //    `case (10, Foo({val = (_, X)})) -> ...use X...;`.
      // Determine whether this is actually a wildcard binding.
      if (pattern.getWrappedValue() instanceof MaybeWildcardPrimitivePattern
          && ((MaybeWildcardPrimitivePattern) pattern.getWrappedValue()).isWildcardBinding()) {
        // The identifier must not already be in use somewhere else, as that shadowing would be confusing.
        IdentifierReferenceTerm wildcardBinding =
            ((MaybeWildcardPrimitivePattern) pattern.getWrappedValue())
                .getOptionalWildcardBinding().get();
        if (scopedHeap.isIdentifierDeclared(wildcardBinding.identifier)) {
          wildcardBinding.logTypeError(
              ClaroTypeException.forIllegalShadowingOfDeclaredVariableForWildcardBinding());
        } else {
          // This is a temporary variable definition that will actually need to get deleted from the symbol table.
          scopedHeap.putIdentifierValue(wildcardBinding.identifier, this.optionalFlattenedMatchedValues.get().get(0));
          scopedHeap.initializeIdentifier(wildcardBinding.identifier);
          this.optionalWildcardBindingName = Optional.of(wildcardBinding.identifier);
        }
        // Mark this pattern as a default pattern since it'll match everything.
        if (foundDefaultCase.get()) {
          // TODO(steving) This is lazy, I should be able to log this error on the actual offending `_`.
          this.matchedExpr.logTypeError(ClaroTypeException.forMatchContainsDuplicateDefaultCases());
        }
        foundDefaultCase.set(true);
      }

      // We have what apparently looks like a user-defined type constructor call. Let's defer to the FunctionCallExpr
      // to validate that the call was parameterized correctly.
      Type impliedPatternType = pattern.toImpliedType(scopedHeap);
      try {
        StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
            Maps.newHashMap(),
            impliedPatternType,
            matchedExprType
        );
      } catch (ClaroTypeException e) {
        // TODO(steving) This is lazy, I should be able to log this error on the actual offending tuple pattern.
        this.matchedExpr.logTypeError(
            ClaroTypeException.forInvalidPatternMatchingWrongType(matchedExprType, impliedPatternType));
      }

      // Ensure that each literal value is unique.
      if (!checkUniqueness.apply(pattern.getWrappedValue())) {
        ((MaybeWildcardPrimitivePattern) pattern.getWrappedValue()).getOptionalWildcardBinding()
            .get()
            .logTypeError(ClaroTypeException.forDuplicateMatchCase());
      }
    } else if (currCasePattern instanceof TypeMatchPattern) {
      TypeMatchPattern<?> currTypeMatchPattern = (TypeMatchPattern<?>) currCasePattern;
      Type impliedPatternType = currTypeMatchPattern.toImpliedType(scopedHeap);
      try {
        StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
            Maps.newHashMap(),
            impliedPatternType,
            matchedExprType
        );
      } catch (ClaroTypeException e) {
        // TODO(steving) This is lazy, I should be able to log this error on the actual offending tuple pattern.
        this.matchedExpr.logTypeError(
            ClaroTypeException.forInvalidPatternMatchingWrongType(matchedExprType, impliedPatternType));
      }
      // Ensure that each literal value is unique.
      if (!checkUniqueness.apply(currTypeMatchPattern)) {
        // TODO(steving) This is lazy, I should be able to log this error on the actual offending tuple pattern.
        this.matchedExpr.logTypeError(ClaroTypeException.forDuplicateMatchCase());
      }
    } else if (currCasePattern instanceof FlattenedTypeMatchPattern) {
      // TODO(steving) TESTING!! DO NOT SUBMIT Here just testing flattening works.
      FlattenedTypeMatchPattern currTypeMatchPattern = (FlattenedTypeMatchPattern) currCasePattern;
      Type impliedPatternType = currTypeMatchPattern.getImpliedType();
      try {
        StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
            Maps.newHashMap(),
            impliedPatternType,
            matchedExprType
        );
      } catch (ClaroTypeException e) {
        // TODO(steving) This is lazy, I should be able to log this error on the actual offending tuple pattern.
        this.matchedExpr.logTypeError(
            ClaroTypeException.forInvalidPatternMatchingWrongType(matchedExprType, impliedPatternType));
      }
      // Ensure that each literal value is unique.
      if (!checkUniqueness.apply(currTypeMatchPattern.getFlattenedPattern())) {
        // TODO(steving) This is lazy, I should be able to log this error on the actual offending tuple pattern.
        this.matchedExpr.logTypeError(ClaroTypeException.forDuplicateMatchCase());
      }
    } else {
      if (foundDefaultCase.get()) {
        // TODO(steving) This is lazy, I should be able to log this error on the actual offending `_`.
        this.matchedExpr.logTypeError(ClaroTypeException.forMatchContainsDuplicateDefaultCases());
      }
      foundDefaultCase.set(true);
    }
  }

  private static Function<Object, Boolean> getCheckUniquenessFn(
      ScopedHeap scopedHeap,
      Type matchedExprType,
      ImmutableList.Builder<String> caseExprLiterals,
      AtomicReference<Optional<HashSet<Type>>> foundTypeLiterals) {
    Function<Object, Boolean> checkUniqueness;
    switch (matchedExprType.baseType()) {
      case BOOLEAN:
        final byte[] foundBoolMask = {0};
        checkUniqueness = e -> {
          if (e instanceof MaybeWildcardPrimitivePattern
              && ((MaybeWildcardPrimitivePattern) e).toImpliedType(scopedHeap).equals(Types.BOOLEAN)) {
            if (((MaybeWildcardPrimitivePattern) e).getOptionalExpr().get().equals(Boolean.TRUE)) {
              caseExprLiterals.add("true");
              if ((foundBoolMask[0] & 2) != 0) {
                return false; // Already found it.
              }
              foundBoolMask[0] |= 2;
            } else {
              caseExprLiterals.add("false");
              if ((foundBoolMask[0] & 1) != 0) {
                return false; // Already found it.
              }
              foundBoolMask[0] |= 1;
            }
          }
          return true;
        };
        break;
      case INTEGER:
        HashSet<Integer> foundIntLiterals = Sets.newHashSet();
        checkUniqueness = e -> {
          if (e instanceof MaybeWildcardPrimitivePattern
              && ((MaybeWildcardPrimitivePattern) e).toImpliedType(scopedHeap).equals(Types.INTEGER)) {
            Integer val = (Integer) ((MaybeWildcardPrimitivePattern) e).getOptionalExpr().get();
            caseExprLiterals.add(val.toString());
            return foundIntLiterals.add(val);
          }
          return true; // Gracefully degrade when we would've already found a type mismatch.
        };
        break;
      case STRING:
        HashSet<String> foundStrLiterals = Sets.newHashSet();
        checkUniqueness = e -> {
          if (e instanceof MaybeWildcardPrimitivePattern
              && ((MaybeWildcardPrimitivePattern) e).toImpliedType(scopedHeap).equals(Types.STRING)) {
            String val = (String) ((MaybeWildcardPrimitivePattern) e).getOptionalExpr().get();
            caseExprLiterals.add(String.format("\"%s\"", val));
            return foundStrLiterals.add(val);
          }
          return true; // Gracefully degrade when we would've already found a type mismatch.
        };
        break;
      case ONEOF:
        foundTypeLiterals.set(Optional.of(Sets.newHashSet()));
        Optional<HashSet<Type>> finalFoundTypeLiterals = foundTypeLiterals.get();
        checkUniqueness = e -> {
          if (e instanceof TypeProviderPattern) {
            Type val = ((TypeProviderPattern) e).toImpliedType(scopedHeap);
            caseExprLiterals.add(val.getJavaSourceClaroType());
            return finalFoundTypeLiterals.get().add(val);
          }
          return true; // Gracefully degrade when we would've already found a type mismatch.
        };
        break;
      case USER_DEFINED_TYPE:
        // Every pattern will just be some Foo(...) so we just want to match the inner pattern.
        HashMap<Type, Type> userDefinedConcreteTypeParamsMap = Maps.newHashMap();
        ImmutableList<Type> matchedExprConcreteTypeParams = matchedExprType.parameterizedTypeArgs().values().asList();
        ImmutableList<String> matchedExprTypeParamNames =
            Types.UserDefinedType.$typeParamNames.get(((Types.UserDefinedType) matchedExprType).getTypeName());
        for (int i = 0; i < matchedExprConcreteTypeParams.size(); i++) {
          userDefinedConcreteTypeParamsMap.put(
              Types.$GenericTypeParam.forTypeParamName(matchedExprTypeParamNames.get(i)),
              matchedExprConcreteTypeParams.get(i)
          );
        }
        Type potentiallyGenericWrappedType =
            Types.UserDefinedType.$resolvedWrappedTypes.get(((Types.UserDefinedType) matchedExprType).getTypeName());
        Type validatedWrappedType;
        try {
          validatedWrappedType =
              StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
                  userDefinedConcreteTypeParamsMap,
                  potentiallyGenericWrappedType,
                  potentiallyGenericWrappedType,
                  /*inferConcreteTypes=*/true
              );
        } catch (ClaroTypeException e) {
          throw new RuntimeException("Internal Compiler Error: Should be unreachable.", e);
        }
        // Really I just want to defer to the underlying wrapped type as we're just unwrapping these user-defined types.
        checkUniqueness = getCheckUniquenessFn(scopedHeap, validatedWrappedType, caseExprLiterals, foundTypeLiterals);
        break;
      case TUPLE:
      case STRUCT:
        ArrayList<ImmutableList<MaybeWildcardPrimitivePattern>> foundStructuredTypePatterns = new ArrayList<>();
        checkUniqueness = e -> {
          if (e instanceof ImmutableList<?>) {
            ImmutableList<MaybeWildcardPrimitivePattern> currFlattenedPattern =
                (ImmutableList<MaybeWildcardPrimitivePattern>) e;
            // Need to exhaustively check this new pattern against all preceding patterns.
            CheckPattern:
            for (ImmutableList<MaybeWildcardPrimitivePattern> precedingPattern : foundStructuredTypePatterns) {
              for (int i = 0; i < currFlattenedPattern.size(); i++) {
                MaybeWildcardPrimitivePattern precedingPatternVal = precedingPattern.get(i);
                MaybeWildcardPrimitivePattern currPatternVal = currFlattenedPattern.get(i);
                if (!(precedingPatternVal.equals(currPatternVal) ||
                      !precedingPatternVal.getOptionalExpr().isPresent())) {
                  // These patterns are distinct, so let's skip straight on to the next pattern.
                  continue CheckPattern; // I love how hacky this is, lol.
                }
              }
              // We made it through all elems w/o finding anything unique, this pattern is not unique.
              return false;
            }
            // This pattern was distinct from all prior patterns, so add to found patterns and signal success.
            return foundStructuredTypePatterns.add(currFlattenedPattern);
          }
          return true; // Gracefully degrade when we would've already found a type mismatch.
        };
        break;
      default:
        throw new RuntimeException("Internal Compiler Error! Should be unreachable.");
    }
    return checkUniqueness;
  }

  private static void validateCaseExpr(ScopedHeap scopedHeap, Type matchedExprType, Function<Object, Boolean> checkUniqueness, Expr currCaseExpr) throws ClaroTypeException {
    // They should all be of the correct type relative to the matched expr.
    currCaseExpr.assertExpectedExprType(scopedHeap, matchedExprType);

    // Ensure that each literal value is unique.
    if (!checkUniqueness.apply(currCaseExpr)) {
      currCaseExpr.logTypeError(ClaroTypeException.forDuplicateMatchCase());
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(new StringBuilder());

    // In case we're dealing with a user-defined type, then we actually will go ahead and handle just the underlying
    // wrapped value instead since we already know that all of the patterns are really just concerning the wrapped value
    // and the outer type is obviously implied.
    Optional<Expr> optionalOriginalMatchedExpr = Optional.empty();
    Optional<Type> optionalOriginalMatchedExprType = Optional.empty();
    Optional<String> optionalSyntheticMatchedWrappedValIdentifier = Optional.empty();
    if (this.matchedExprType.baseType().equals(BaseType.USER_DEFINED_TYPE)) {
      this.optionalUnwrapMatchedExpr = Optional.of(
          new UnwrapUserDefinedTypeExpr(
              this.matchedExpr, this.matchedExpr.currentLine, this.matchedExpr.currentLineNumber, this.matchedExpr.startCol, this.matchedExpr.endCol));
      try {
        this.optionalUnwrapMatchedExpr.get().getValidatedExprType(scopedHeap);
      } catch (ClaroTypeException e) {
        throw new RuntimeException("Internal Compiler Error! Should be unreachable.");
      }
      optionalOriginalMatchedExpr = Optional.of(this.matchedExpr);
      optionalOriginalMatchedExprType = Optional.of(this.matchedExprType);
      this.matchedExpr = this.optionalUnwrapMatchedExpr.get();
      this.matchedExprType = this.optionalUnwrapMatchedExpr.get().validatedUnwrappedType;

      // I may also need to declare a synthetic variable for the sake of any wildcard binding having something to refer
      // back to.
      if (this.optionalWildcardBindingName.isPresent()) {
        optionalSyntheticMatchedWrappedValIdentifier = Optional.of("$syntheticMatchedWrappedValIdentifier");
        res.javaSourceBody()
            .append("{// Begin match(unwrap(<user-defined type>)) block\n")
            .append(this.matchedExprType.getJavaSourceType())
            .append(" ")
            .append(optionalSyntheticMatchedWrappedValIdentifier.get())
            .append(";\n");
      }
    }

    switch (this.matchedExprType.baseType()) {
      // Matches of INTEGER/STRING can simply be congen'd as a Java switch.
      case INTEGER:
      case STRING:
        res.javaSourceBody().append("switch (");
        if (optionalSyntheticMatchedWrappedValIdentifier.isPresent()) {
          // Hold onto the unwrapped value inline in case there's a wildcard binding to this thing.
          res.javaSourceBody()
              .append(optionalSyntheticMatchedWrappedValIdentifier.get())
              .append(" = ");
        }
        res = res.createMerged(this.matchedExpr.generateJavaSourceOutput(scopedHeap));
        res.javaSourceBody().append(") {\n");
        for (int i = 0, caseLiteralsInd = 0; i < this.cases.size(); i++, caseLiteralsInd++) {
          ImmutableList<Object> currCase = this.cases.get(i);
          boolean usesWildcardBinding = false;
          if (currCase.get(0) instanceof MaybeWildcardPrimitivePattern
              || (currCase.get(0) instanceof UserDefinedTypePattern
                  &&
                  !(usesWildcardBinding =
                        ((UserDefinedTypePattern) currCase.get(0)).getWrappedValue() instanceof MaybeWildcardPrimitivePattern
                        &&
                        ((MaybeWildcardPrimitivePattern) ((UserDefinedTypePattern) currCase.get(0)).getWrappedValue()).isWildcardBinding()))) {
            res.javaSourceBody()
                .append("\tcase ")
                .append(this.caseExprLiterals.get(caseLiteralsInd))
                .append(":\n");
          } else if (currCase.get(0) instanceof ImmutableList) { // Must be a multi-match case.
            for (int j = 0; j < ((ImmutableList<?>) currCase.get(0)).size(); j++) {
              res.javaSourceBody()
                  .append("\tcase ")
                  .append(this.caseExprLiterals.get(caseLiteralsInd++))
                  .append(":\n");
            }
            --caseLiteralsInd; // Correct for the fact we're in a for-loop...super weird but meh.
          } else { // Must be the default wildcard.
            res.javaSourceBody().append("\tdefault:");
          }

          // Each branch gets isolated to its own scope.
          scopedHeap.enterNewScope();
          if (usesWildcardBinding) {
            String wildcardBindingName =
                ((MaybeWildcardPrimitivePattern) ((UserDefinedTypePattern) currCase.get(0)).getWrappedValue())
                    .getOptionalWildcardBinding().get().identifier;
            scopedHeap.putIdentifierValue(wildcardBindingName, this.matchedExprType);
            scopedHeap.initializeIdentifier(wildcardBindingName);
            res.javaSourceBody()
                .append("\n")
                .append(matchedExprType.getJavaSourceType())
                .append(" ")
                .append(wildcardBindingName)
                .append(" = ")
                .append(optionalSyntheticMatchedWrappedValIdentifier.get())
                .append(";\n");
          }
          res = res.createMerged(((StmtListNode) currCase.get(1)).generateJavaSourceOutput(scopedHeap));
          scopedHeap.exitCurrScope();
          res.javaSourceBody().append("break;\n");
        }
        res.javaSourceBody().append("}\n");
        break;
      case BOOLEAN:
        // This will actually just get codegen'd into an if-else. Honestly, the programmer should've just written this
        // in the first place most likely.
        res.javaSourceBody().append("if (");
        res = res.createMerged(this.matchedExpr.generateJavaSourceOutput(scopedHeap));
        res.javaSourceBody().append(") {\n");
        int indexOfTrueStmts = this.caseExprLiterals.get(0).equals("true") ? 0 : 1;

        // Each branch gets isolated to its own scope.
        scopedHeap.enterNewScope();
        res = res.createMerged(
            ((StmtListNode) this.cases.get(indexOfTrueStmts).get(1)).generateJavaSourceOutput(scopedHeap));
        scopedHeap.exitCurrScope();

        res.javaSourceBody().append("} else {\n");

        // Each branch gets isolated to its own scope.
        scopedHeap.enterNewScope();
        res = res.createMerged(
            ((StmtListNode) this.cases.get((indexOfTrueStmts + 1) % 2).get(1)).generateJavaSourceOutput(scopedHeap));
        scopedHeap.exitCurrScope();

        res.javaSourceBody().append("}\n");
        break;
      case ONEOF:
        res.javaSourceBody().append("{// Begin match(<oneof<...>>) block\n");
        String matchedOneofIdentifier;
        if (this.matchedExpr instanceof IdentifierReferenceTerm) {
          matchedOneofIdentifier = ((IdentifierReferenceTerm) this.matchedExpr).identifier;
        } else {
          matchedOneofIdentifier = "$syntheticMatchedOneofVal";
          res.javaSourceBody().append("Object ").append(matchedOneofIdentifier).append(" = ");
          res = res.createMerged(this.matchedExpr.generateJavaSourceOutput(scopedHeap));
          res.javaSourceBody().append(";\n");
        }
        res.javaSourceBody().append("if (ClaroRuntimeUtilities.getClaroType(")
            .append(matchedOneofIdentifier)
            .append(").equals(")
            .append(this.caseExprLiterals.get(0))
            .append(")) {\n");

        // Each branch gets isolated to its own scope.
        scopedHeap.enterNewScope();
        res = res.createMerged(((StmtListNode) this.cases.get(0).get(1)).generateJavaSourceOutput(scopedHeap));
        scopedHeap.exitCurrScope();
        res.javaSourceBody().append("} ");

        boolean hasDefaultCase = this.cases.get(this.cases.size() - 1).get(0).equals(false);
        for (int i = 1; i < this.caseExprLiterals.size() - (hasDefaultCase ? 0 : 1); i++) {
          res.javaSourceBody().append("else if (ClaroRuntimeUtilities.getClaroType(")
              .append(matchedOneofIdentifier)
              .append(").equals(")
              .append(this.caseExprLiterals.get(i))
              .append(")) {\n");
          // Each branch gets isolated to its own scope.
          scopedHeap.enterNewScope();
          res = res.createMerged(((StmtListNode) this.cases.get(i).get(1)).generateJavaSourceOutput(scopedHeap));
          scopedHeap.exitCurrScope();
          res.javaSourceBody().append("}\n");
        }
        res.javaSourceBody().append("else {\n");
        // Each branch gets isolated to its own scope.
        scopedHeap.enterNewScope();
        res = res.createMerged(
            ((StmtListNode) this.cases.get(this.cases.size() - 1).get(1)).generateJavaSourceOutput(scopedHeap));
        scopedHeap.exitCurrScope();
        res.javaSourceBody().append("}\n");

        res.javaSourceBody().append("} // End match(<oneof<...>>) block\n");
        break;
      case TUPLE:
      case STRUCT:
        res.javaSourceBody().append("$Match").append(this.matchId).append(" : { // Begin structured type match. \n");
        String syntheticMatchedStructuredTypeIdentifier;
        if (matchedExpr instanceof IdentifierReferenceTerm) {
          syntheticMatchedStructuredTypeIdentifier = ((IdentifierReferenceTerm) matchedExpr).identifier;
        } else {
          syntheticMatchedStructuredTypeIdentifier = "$syntheticMatchedStructuredTypeIdentifier";
          res.javaSourceBody()
              .append(this.matchedExprType.getJavaSourceType())
              .append(" ")
              .append(syntheticMatchedStructuredTypeIdentifier)
              .append(" = ");
          res = res.createMerged(this.matchedExpr.generateJavaSourceOutput(scopedHeap));
          res.javaSourceBody().append(";\n");
        }
        codegenNestedValueDestructuring(
            this.matchedExprType, new Stack<>(), 0, syntheticMatchedStructuredTypeIdentifier, res);
        Stack<ImmutableList<Object>> casesStack = new Stack<>();
        this.cases.reverse().stream()
            .map(
                l -> ImmutableList.of(
                    l.get(0) instanceof FlattenedTypeMatchPattern
                    ? ((FlattenedTypeMatchPattern) l.get(0)).getFlattenedPattern()
                    : Boolean.FALSE,
                    l.get(1)
                ))
            .forEach(casesStack::push);
        AtomicReference<GeneratedJavaSource> codegen = new AtomicReference<>(res);
        codegenDestructuredSequenceMatch(casesStack, 0, codegen, scopedHeap, this.matchId);
        res = codegen.get();
        res.javaSourceBody().append("} // End structured type match. \n");
        break;
      default:
        throw new RuntimeException("Internal Compiler Error! Should be unreachable.");
    }

    // In case this codegen is happening within a monomorphized procedure, it'll be a good idea to reset the state we
    // updated so that on following codegens over this node, this codegen remains idempotent.
    if (optionalUnwrapMatchedExpr.isPresent()) {
      this.matchedExpr = optionalOriginalMatchedExpr.get();
      this.matchedExprType = optionalOriginalMatchedExprType.get();
      res.javaSourceBody()
          .append("}// End match(unwrap(<user-defined type>)) block\n");
    }

    return res;
  }

  // This function is used to recursively traverse an arbitrarily nested type to codegen destructuring of its primitive
  // values into variables in a flat scope named like $v0, ..., $vn.
  private static int codegenNestedValueDestructuring(
      Type matchedExprType, Stack<String> path, int count, String matchedValIdentifier, GeneratedJavaSource codegen) {
    ImmutableList<Type> elementTypes;
    BiFunction<Type, Integer, String> getElementAccessPattern;
    switch (matchedExprType.baseType()) {
      case TUPLE:
        elementTypes = ((Types.TupleType) matchedExprType).getValueTypes();
        getElementAccessPattern = (type, i) -> "((" + type.getJavaSourceType() + ") %s.getElement(" + i + "))";
        break;
      case STRUCT:
        elementTypes = ((Types.StructType) matchedExprType).getFieldTypes();
        getElementAccessPattern = (type, i) -> "((" + type.getJavaSourceType() + ") %s.values[" + i + "])";
        break;
      case USER_DEFINED_TYPE:
        HashMap<Type, Type> userDefinedConcreteTypeParamsMap = Maps.newHashMap();
        ImmutableList<Type> matchedExprConcreteTypeParams = matchedExprType.parameterizedTypeArgs().values().asList();
        ImmutableList<String> matchedExprTypeParamNames =
            Types.UserDefinedType.$typeParamNames.get(((Types.UserDefinedType) matchedExprType).getTypeName());
        for (int i = 0; i < matchedExprConcreteTypeParams.size(); i++) {
          userDefinedConcreteTypeParamsMap.put(
              Types.$GenericTypeParam.forTypeParamName(matchedExprTypeParamNames.get(i)),
              matchedExprConcreteTypeParams.get(i)
          );
        }
        Type potentiallyGenericWrappedType =
            Types.UserDefinedType.$resolvedWrappedTypes.get(((Types.UserDefinedType) matchedExprType).getTypeName());
        Type validatedWrappedType;
        try {
          validatedWrappedType =
              StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
                  userDefinedConcreteTypeParamsMap,
                  potentiallyGenericWrappedType,
                  potentiallyGenericWrappedType,
                  /*inferConcreteTypes=*/true
              );
        } catch (ClaroTypeException e) {
          throw new RuntimeException("Internal Compiler Error: Should be unreachable.", e);
        }
        elementTypes = ImmutableList.of(validatedWrappedType);
        getElementAccessPattern = (type, i) -> "((" + type.getJavaSourceType() + ") %s.wrappedValue)";
        break;
      default:
        throw new RuntimeException("Internal Compiler Error! Should be unreachable.");
    }

    for (int i = 0; i < elementTypes.size(); i++) {
      Type elemType = elementTypes.get(i);
      path.push(getElementAccessPattern.apply(elemType, i));
      if (elemType instanceof ConcreteType) {
        codegen.javaSourceBody()
            .append(elemType.getJavaSourceType())
            .append(" ")
            .append("$v")
            .append(count++)
            .append(" = ");
        String destructuredValCodegen = matchedValIdentifier;
        for (String pathComponent : path) {
          // Need to compose the accesses so that the necessary casts are associated correctly.
          destructuredValCodegen = String.format(pathComponent, destructuredValCodegen);
        }
        codegen.javaSourceBody()
            .append(destructuredValCodegen)
            .append(";\n");
      } else {
        count = codegenNestedValueDestructuring(elemType, path, count, matchedValIdentifier, codegen);
      }
      path.pop();
    }
    return count;
  }

  // This function is used to flatten arbitrarily nested patterns into a flat sequence of patterns so that they can be
  // switched over using general-purpose logic that's not concerned with the particular structuring of the type.
  private static ImmutableList<ImmutableList<Object>> flattenNestedPatterns(
      ImmutableList<ImmutableList<Object>> patterns, Type matchedExprType, ScopedHeap scopedHeap) throws ClaroTypeException {
    ImmutableList.Builder<ImmutableList<Object>> flatPatternsStack = ImmutableList.builder();
    for (ImmutableList<Object> pattern : patterns) {
      if (pattern.get(0) instanceof Boolean) { // case _ -> ...;
        flatPatternsStack.add(pattern);
      } else { // case (1, 2, _) -> ...;
        ImmutableList.Builder<MaybeWildcardPrimitivePattern> currFlattenedPattern = ImmutableList.builder();
        flattenPattern((TypeMatchPattern<Type>) pattern.get(0), matchedExprType, currFlattenedPattern, scopedHeap);
        flatPatternsStack.add(
            ImmutableList.of(
                FlattenedTypeMatchPattern.create(
                    currFlattenedPattern.build(), ((TypeMatchPattern<?>) pattern.get(0)).toImpliedType(scopedHeap)),
                pattern.get(1)
            ));
      }
    }
    return flatPatternsStack.build();
  }

  private static void flattenPattern(
      TypeMatchPattern<? extends Type> pattern, Type matchedType, ImmutableList.Builder<MaybeWildcardPrimitivePattern> currFlattenedPattern, ScopedHeap scopedHeap) throws ClaroTypeException {
    // Handle wildcards first.
    if (pattern instanceof MaybeWildcardPrimitivePattern
        && !((MaybeWildcardPrimitivePattern) pattern).getOptionalExpr().isPresent()) {
      if (matchedType instanceof ConcreteType) { // Wildcard matching a primitive.
        currFlattenedPattern.add((MaybeWildcardPrimitivePattern) pattern);
      } else { // Wildcard matching a nested type.
        ImmutableList<Type> elementTypes;
        switch (matchedType.baseType()) {
          case TUPLE:
            elementTypes = ((Types.TupleType) matchedType).getValueTypes();
            break;
          case STRUCT:
            elementTypes = ((Types.StructType) matchedType).getFieldTypes();
            break;
          default:
            throw new RuntimeException("Internal Compiler Error! Should be unreachable.");
        }
        // Now iterate over the types themselves to put a wildcard for every single primitive.
        for (Type elemType : elementTypes) {
          flattenPattern(pattern, elemType, currFlattenedPattern, scopedHeap);
        }
      }
      return;
    }

    // Handle nested patterns here.
    ImmutableList<? extends TypeMatchPattern<?>> patternParts;
    ImmutableList<Type> patternTypes;
    if (pattern instanceof TuplePattern) {
      patternParts = ((TuplePattern) pattern).getElementValues();
      patternTypes = ((Types.TupleType) matchedType).getValueTypes();
    } else if (pattern instanceof StructPattern) {
      patternParts = ((StructPattern) pattern).getFieldPatterns()
          .stream()
          .map(StructFieldPattern::getValue)
          .collect(ImmutableList.toImmutableList());
      patternTypes = ((Types.StructType) matchedType).getFieldTypes();
    } else if (pattern instanceof UserDefinedTypePattern) {
      patternParts = ImmutableList.of(((UserDefinedTypePattern) pattern).getWrappedValue());
      patternTypes = ImmutableList.of(((UserDefinedTypePattern) pattern).toImpliedType(scopedHeap));
    } else {
      System.err.println("TESTING!!! FOUND UNEXPECTED PATTERN: " + pattern);
      throw new RuntimeException("Internal Compiler Error! Should be unreachable.");
    }
    for (int i = 0; i < patternParts.size(); i++) {
      if (patternParts.get(i) instanceof MaybeWildcardPrimitivePattern
          && ((MaybeWildcardPrimitivePattern) patternParts.get(i)).getOptionalExpr().isPresent()) {
        currFlattenedPattern.add((MaybeWildcardPrimitivePattern) patternParts.get(i));
      } else {
        flattenPattern(patternParts.get(i), patternTypes.get(i), currFlattenedPattern, scopedHeap);
      }
    }
  }

  // This function will be used to perform codegen for any arbitrarily nested structure that's already had its values
  // de-structured into a flat sequence of values represented as variables $v0, ..., $vn. This modelling is very
  // powerful as it allows the codegen for all manners of structured types to be rendered consistently using efficient
  // switching.
  private static void codegenDestructuredSequenceMatch(Stack<ImmutableList<Object>> cases, int startInd, AtomicReference<GeneratedJavaSource> res, ScopedHeap scopedHeap, long matchId) {
    while (!cases.isEmpty() && !cases.peek().get(0).equals(false)) {
      ImmutableList<Object> top = cases.pop();
      int wildcardPrefixLen =
          countWildcardFromInd((ImmutableList<MaybeWildcardPrimitivePattern>) top.get(0), startInd);

      // If it's all wildcards to the end, then you just need to fall straight into the codegen below.
      if (wildcardPrefixLen + startInd < ((ImmutableList<?>) top.get(0)).size()) {
        Stack<ImmutableList<Object>> switchGroup = new Stack<>();
        switchGroup.push(top);
        while (!cases.isEmpty()
               && !cases.peek().get(0).equals(false)
               && countWildcardFromInd((ImmutableList<MaybeWildcardPrimitivePattern>) cases.peek().get(0), startInd) ==
                  wildcardPrefixLen) {
          switchGroup.push(cases.pop());
        }

        Collections.reverse(switchGroup);
        CodegenSwitchGroup(switchGroup, startInd + wildcardPrefixLen, res, scopedHeap, matchId);
      } else {
        // Just go straight to codegen on `case _ ->` or `case (..., _, _)`
        res.updateAndGet(
            codegen -> codegen.createMerged(((StmtListNode) top.get(1)).generateJavaSourceOutput(scopedHeap)));
        // This is an unfortunate hack since I don't want to have to figure out how to avoid adding trailing `break`s
        // if they'd happen to be unreachable beyond these `break $MatchN` clauses. I've already validated using javap
        // that all of this gets optimized out of the JVM bytecode in the final class file, so this doesn't actually
        // matter at the end of the day.
        res.get().javaSourceBody().append("if (true) { break $Match").append(matchId).append("; }\n");
      }
    }

    if (!cases.isEmpty() && cases.peek().get(0).equals(false)) {
      // Just go straight to codegen on `case _ ->` or `case (..., _, _)`
      res.updateAndGet(
          codegen -> codegen.createMerged(((StmtListNode) cases.pop().get(1)).generateJavaSourceOutput(scopedHeap)));
      res.get().javaSourceBody().append("if (true) { break $Match").append(matchId).append("; }\n");
    }
  }

  private static void CodegenSwitchGroup(Stack<ImmutableList<Object>> switchGroup, int startInd, AtomicReference<GeneratedJavaSource> res, ScopedHeap scopedHeap, long matchId) {
    res.get()
        .javaSourceBody()
        .append("switch ($v")
        .append(startInd)
        .append(") {\n");
    while (!switchGroup.isEmpty()) {
      Optional<Object> currCase =
          ((ImmutableList<MaybeWildcardPrimitivePattern>) switchGroup.peek().get(0)).get(startInd).getOptionalExpr();
      Stack<ImmutableList<Object>> caseGroup = new Stack<>();
      for (ImmutableList<Object> p : switchGroup) {
        if (((ImmutableList<MaybeWildcardPrimitivePattern>) p.get(0)).get(startInd)
            .getOptionalExpr()
            .equals(currCase)) {
          caseGroup.add(p);
        }
      }
      switchGroup.removeAll(caseGroup);

      res.get()
          .javaSourceBody()
          .append("\tcase ")
          .append(currCase.get() instanceof String
                  ? String.format("\"%s\"", currCase.get())
                  : currCase.get().toString())
          .append(":\n");
      codegenDestructuredSequenceMatch(caseGroup, startInd + 1, res, scopedHeap, matchId);
      res.get().javaSourceBody().append("break;\n");
    }

    res.get().javaSourceBody().append("}\n");
  }

  private static int countWildcardFromInd(ImmutableList<MaybeWildcardPrimitivePattern> l, int i) {
    int count = 0;
    while (i < l.size() && !l.get(i).getOptionalExpr().isPresent()) {
      count++;
      i++;
    }
    return count;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Eventually need to impl match when I come back to adding support for the interpreted backend.
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support match in the interpreted backend just yet!");
  }

  // This class will be used by the parser to signal that a User-Defined type was matched.
  // TODO(steving) TESTING!!! I'll need to rework this to take a TypeMatchPattern<?> instead of an Expr for the wrapped
  //   value because otherwise for any user-defined type, you'll be unable to use any wildcard patterns. This is
  //   actually particularly difficult because I'll end up needing to rewrite the logic for inferring the parameterized
  //   types that currently FunctionCallExpr is doing for me here. Basically I'll have to rewrite the core logic of
  //   Structural type validation and handle wildcards as generic type params, and handle type providers in oneof places.
  @AutoValue
  public abstract static class UserDefinedTypePattern implements TypeMatchPattern<Types.UserDefinedType> {
    public abstract IdentifierReferenceTerm getTypeName();

    public abstract TypeMatchPattern<? extends Type> getWrappedValue();

    public static UserDefinedTypePattern forTypeNameAndWrappedValue(IdentifierReferenceTerm typeName, TypeMatchPattern<? extends Type> wrappedValue) {
      return new AutoValue_MatchStmt_UserDefinedTypePattern(typeName, wrappedValue);
    }

    @Override
    public Types.UserDefinedType toImpliedType(ScopedHeap scopedHeap) throws ClaroTypeException {
      // We have what apparently looks like a user-defined type constructor call. Let's defer to the FunctionCallExpr
      // to validate that the call was parameterized correctly.
      return (Types.UserDefinedType)
          this.getSyntheticFunctionCallExprForTypeValidation()
              .getValidatedExprType(scopedHeap);
    }

    private FunctionCallExpr getSyntheticFunctionCallExprForTypeValidation() {
      return
          new FunctionCallExpr(
              this.getTypeName().identifier,
              ImmutableList.of(
                  new Expr(
                      ImmutableList.of(),
                      this.getTypeName().currentLine,
                      this.getTypeName().currentLineNumber,
                      this.getTypeName().startCol,
                      this.getTypeName().endCol
                  ) {
                    @Override
                    public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
                      return UserDefinedTypePattern.this.getWrappedValue().toImpliedType(scopedHeap);
                    }

                    @Override
                    public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
                      throw new RuntimeException("Internal Compiler Error! Should be unreachable.");
                    }
                  }),
              this.getTypeName().currentLine,
              this.getTypeName().currentLineNumber,
              this.getTypeName().startCol,
              this.getTypeName().endCol
          );
    }
  }

  public interface TypeMatchPattern<T extends Type> {
    T toImpliedType(ScopedHeap scopedHeap) throws ClaroTypeException;
  }

  // This class will be used by the parser to signal that a Tuple was matched.
  @AutoValue
  public abstract static class TuplePattern implements TypeMatchPattern<Types.TupleType> {
    public abstract ImmutableList<TypeMatchPattern<? extends Type>> getElementValues();

    public abstract boolean getIsMutable();

    public static TuplePattern forElementValues(ImmutableList<TypeMatchPattern<? extends Type>> elementValues, boolean isMutable) {
      return new AutoValue_MatchStmt_TuplePattern(elementValues, isMutable);
    }

    public Types.TupleType toImpliedType(ScopedHeap scopedHeap) throws ClaroTypeException {
      ImmutableList.Builder<Type> valueTypes = ImmutableList.builder();
      for (TypeMatchPattern<?> p : this.getElementValues()) {
        valueTypes.add(p.toImpliedType(scopedHeap));
      }
      return Types.TupleType.forValueTypes(
          valueTypes.build(),
          /*isMutable=*/this.getIsMutable()
      );
    }
  }

  // This class will be used by the parser to signal that a Struct was matched.
  @AutoValue
  public abstract static class StructPattern implements TypeMatchPattern<Types.StructType> {
    public abstract ImmutableList<StructFieldPattern> getFieldPatterns();

    public abstract boolean getIsMutable();

    public static StructPattern forFieldPatterns(ImmutableList<StructFieldPattern> fieldPatterns, boolean isMutable) {
      return new AutoValue_MatchStmt_StructPattern(fieldPatterns, isMutable);
    }

    @Override
    public Types.StructType toImpliedType(ScopedHeap scopedHeap) throws ClaroTypeException {
      ImmutableList.Builder<Type> fieldImpliedTypes = ImmutableList.builder();
      for (StructFieldPattern fieldPattern : this.getFieldPatterns()) {
        fieldImpliedTypes.add(fieldPattern.getValue().toImpliedType(scopedHeap));
      }
      return Types.StructType.forFieldTypes(
          this.getFieldPatterns()
              .stream()
              .map(f -> f.getFieldName().identifier)
              .collect(ImmutableList.toImmutableList()),
          fieldImpliedTypes.build(),
          this.getIsMutable()
      );
    }
  }

  @AutoValue
  public abstract static class StructFieldPattern {
    public abstract IdentifierReferenceTerm getFieldName();

    public abstract TypeMatchPattern<? extends Type> getValue();

    public static StructFieldPattern forFieldNameAndValue(IdentifierReferenceTerm fieldName, TypeMatchPattern<? extends Type> fieldValue) {
      return new AutoValue_MatchStmt_StructFieldPattern(fieldName, fieldValue);
    }
  }

  @AutoValue
  public abstract static class FlattenedTypeMatchPattern {
    public abstract ImmutableList<MaybeWildcardPrimitivePattern> getFlattenedPattern();

    public abstract Type getImpliedType();

    public static FlattenedTypeMatchPattern create(
        ImmutableList<MaybeWildcardPrimitivePattern> flattenedPattern,
        Type impliedType) {
      return new AutoValue_MatchStmt_FlattenedTypeMatchPattern(flattenedPattern, impliedType);
    }
  }

  // This class will be used by the parser to signal that a nested Wildcard was matched.
  @AutoValue
  public abstract static class MaybeWildcardPrimitivePattern implements TypeMatchPattern<Type> {
    private static long globalWildcardCount = 0;

    // Two Optionals representing only 3 valid states... here's a perfect example of where Claro will give me better
    // tools for reasoning about correctness than Java gives me with reasonable effort.
    public abstract Optional<Object> getOptionalExpr();

    public abstract Optional<IdentifierReferenceTerm> getOptionalWildcardBinding();

    public abstract Optional<Long> getOptionalWildcardId();


    public static MaybeWildcardPrimitivePattern forNullableExpr(Expr nullableExpr) {
      if (nullableExpr == null) {
        return new AutoValue_MatchStmt_MaybeWildcardPrimitivePattern(Optional.empty(), Optional.empty(), Optional.of(MaybeWildcardPrimitivePattern.globalWildcardCount++));
      } else if (nullableExpr instanceof IntegerTerm) {
        return new AutoValue_MatchStmt_MaybeWildcardPrimitivePattern(Optional.of(((IntegerTerm) nullableExpr).getValue()), Optional.empty(), Optional.empty());
      } else if (nullableExpr instanceof StringTerm) {
        return new AutoValue_MatchStmt_MaybeWildcardPrimitivePattern(Optional.of(((StringTerm) nullableExpr).getValue()), Optional.empty(), Optional.empty());
      } else if (nullableExpr instanceof TrueTerm) {
        return new AutoValue_MatchStmt_MaybeWildcardPrimitivePattern(Optional.of(((TrueTerm) nullableExpr).getValue()), Optional.empty(), Optional.empty());
      } else if (nullableExpr instanceof FalseTerm) {
        return new AutoValue_MatchStmt_MaybeWildcardPrimitivePattern(Optional.of(((FalseTerm) nullableExpr).getValue()), Optional.empty(), Optional.empty());
      }
      throw new RuntimeException("Internal Compiler Error: Should be unreachable.");
    }

    public static MaybeWildcardPrimitivePattern forWildcardBinding(IdentifierReferenceTerm wildcardBinding) {
      return new AutoValue_MatchStmt_MaybeWildcardPrimitivePattern(Optional.empty(), Optional.of(wildcardBinding), Optional.of(MaybeWildcardPrimitivePattern.globalWildcardCount++));
    }

    @Override
    public Type toImpliedType(ScopedHeap unusedScopedHeap) {
      if (this.getOptionalExpr().isPresent()) {
        Object primitiveLiteral = this.getOptionalExpr().get();
        if (primitiveLiteral instanceof String) {
          return Types.STRING;
        } else if (primitiveLiteral instanceof Integer) {
          return Types.INTEGER;
        } else if (primitiveLiteral instanceof Boolean) {
          return Types.BOOLEAN;
        } else {
          throw new RuntimeException("Internal Compiler Error: Should be unreachable.");
        }
      } else {
        return Types.$GenericTypeParam.forTypeParamName("$_" + this.getOptionalWildcardId().get() + "_");
      }
    }

    public boolean isWildcardBinding() {
      return !this.getOptionalExpr().isPresent() && this.getOptionalWildcardBinding().isPresent();
    }
  }

  @AutoValue
  public abstract static class TypeProviderPattern implements TypeMatchPattern<Type> {
    public abstract TypeProvider getMatchedTypeProvider();

    public static TypeProviderPattern forTypeProvider(TypeProvider matchedTypeProvider) {
      return new AutoValue_MatchStmt_TypeProviderPattern(matchedTypeProvider);
    }

    @Override
    public Type toImpliedType(ScopedHeap scopedHeap) {
      return this.getMatchedTypeProvider().resolveType(scopedHeap);
    }
  }
}
