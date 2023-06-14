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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MatchStmt extends Stmt {
  private static long globalMatchCount = 0;
  private final Expr matchedExpr;
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
    Type matchedExprType = this.matchedExpr.getValidatedExprType(scopedHeap);
    // All base values must be of some supported primitive type (int/string/boolean).
    this.optionalFlattenedMatchedValues = validateMatchedExprTypeIsSupported(matchedExprType, scopedHeap);

    ImmutableList.Builder<String> caseExprLiterals = ImmutableList.builder();
    AtomicReference<Optional<HashSet<Type>>> foundTypeLiterals = new AtomicReference<>(Optional.empty());
    Function<Object, Boolean> checkUniqueness =
        getCheckUniquenessFn(scopedHeap, matchedExprType, caseExprLiterals, foundTypeLiterals);
    // Flatten and validate that all patterns have the correct implied type.
    int countErrorsBefore = Expr.typeErrorsFound.size();
    // TODO(steving) TESTING! This is limiting support for multi-pattern-cases.
    if (this.cases.stream().noneMatch(l -> l.get(0) instanceof ImmutableList)) {
      this.cases = flattenNestedPatterns(this.cases, matchedExprType, scopedHeap);
    }
    if (Expr.typeErrorsFound.size() > countErrorsBefore) {
      // There's nothing to do but try to do best effort type validation of the stmt lists and then exit.
      for (StmtListNode stmtListNode : this.cases.stream()
          .map(l -> (StmtListNode) l.get(1))
          .collect(Collectors.toList())) {
        stmtListNode.assertExpectedExprTypes(scopedHeap);
      }
      return;
    }

    // Need to ensure that at the very least our cases look reasonable.
    AtomicBoolean foundDefaultCase = new AtomicBoolean(false);
    for (int i = 0; i < this.cases.size(); i++) {
      // Each branch gets isolated to its own scope.
      scopedHeap.observeNewScope(/*beginIdentifierInitializationBranchInspection=*/true);

      ImmutableList<Object> currCase = this.cases.get(i);
      AtomicBoolean narrowingRequired = new AtomicBoolean(false);
      if (currCase.get(0) instanceof ImmutableList) {
        for (Object currCasePattern : (ImmutableList<Object>) currCase.get(0)) {
          validateCasePattern(scopedHeap, matchedExprType, checkUniqueness, foundDefaultCase, currCasePattern, narrowingRequired);
        }
      } else {
        validateCasePattern(scopedHeap, matchedExprType, checkUniqueness, foundDefaultCase, currCase.get(0), narrowingRequired);
      }

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
            narrowedTypeSyntheticIdentifier,
            ((FlattenedTypeMatchPattern) currCase.get(0)).getFlattenedPattern().get(0).toImpliedType(scopedHeap),
            null
        );
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
    }

    if (!foundDefaultCase.get()) {
      // Let's find out if a default case is needed.
      Optional<ImmutableList<Object>> optionalNonExhaustivePatternsCounterExample =
          validatePatternsExhaustiveness(
              this.optionalFlattenedMatchedValues.get(),
              this.cases.stream()
                  .map(c -> ((FlattenedTypeMatchPattern) c.get(0)).getFlattenedPattern().stream()
                      .map(e -> e.getOptionalExpr().orElse(e))
                      .collect(ImmutableList.toImmutableList()))
                  .collect(ImmutableList.toImmutableList()),
              this.optionalFlattenedMatchedValues.get().size(),
              scopedHeap
          );

      if (optionalNonExhaustivePatternsCounterExample.isPresent()) {
        // TODO(steving) This is an ugly error, it shouldn't point at the matched expr, it should point at the `match` keyword.
        this.matchedExpr.logTypeError(
            ClaroTypeException.forMatchIsNotExhaustiveOverAllPossibleValues(
                matchedExprType,
                reconstructCounterExample(matchedExprType, optionalNonExhaustivePatternsCounterExample.get()).toString()
            ));
      }
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
      // Check if this is actually a default pattern in disguise.
      if (currCasePattern instanceof MaybeWildcardPrimitivePattern
          && !((MaybeWildcardPrimitivePattern) currCasePattern).getOptionalExpr().isPresent()) {
        if (foundDefaultCase.get()) {
          // TODO(steving) This is lazy, I should be able to log this error on the actual offending `_`.
          this.matchedExpr.logTypeError(ClaroTypeException.forMatchContainsDuplicateDefaultCases());
        }
        foundDefaultCase.set(true);
      }
    } else if (currCasePattern instanceof FlattenedTypeMatchPattern) {
      FlattenedTypeMatchPattern currTypeMatchPattern = (FlattenedTypeMatchPattern) currCasePattern;
      // Ensure that each literal value is unique.
      if (!checkUniqueness.apply(currTypeMatchPattern.getFlattenedPattern())) {
        // TODO(steving) This is lazy, I should be able to log this error on the actual offending tuple pattern.
        this.matchedExpr.logTypeError(ClaroTypeException.forDuplicateMatchCase());
      }
      // Check if this is actually a default pattern in disguise.
      if (((FlattenedTypeMatchPattern) currCasePattern).getFlattenedPattern().stream()
          .noneMatch(p -> p.getOptionalExpr().isPresent())) {
        if (foundDefaultCase.get()) {
          // TODO(steving) This is lazy, I should be able to log this error on the actual offending `_`.
          this.matchedExpr.logTypeError(ClaroTypeException.forMatchContainsDuplicateDefaultCases());
        }
        foundDefaultCase.set(true);
      }
      // Try to trigger narrowing if possible. Note, that oneof type literal bindings aren't "narrowings".
      if (currTypeMatchPattern.getFlattenedPattern().size() == 1
          && currTypeMatchPattern.getFlattenedPattern().get(0).getOptionalExpr().isPresent()
          && currTypeMatchPattern.getFlattenedPattern().get(0).getOptionalExpr().get() instanceof TypeProvider
          && this.matchedExpr instanceof IdentifierReferenceTerm) {
        narrowingRequired.set(true);
      }
      // Finally, attempt to declare variables for any necessary wildcard bindings.
      HashSet<Long> alreadyHandledWildcardBindings = Sets.newHashSet();
      for (MaybeWildcardPrimitivePattern wildcardBindingPattern :
          ((FlattenedTypeMatchPattern) currCasePattern).getFlattenedPattern().stream()
              .filter(MaybeWildcardPrimitivePattern::isWildcardBinding)
              .collect(Collectors.toList())) {
        // The identifier must not already be in use somewhere else, as that shadowing would be confusing.
        IdentifierReferenceTerm wildcardBinding = wildcardBindingPattern.getOptionalWildcardBinding().get();
        if (!alreadyHandledWildcardBindings.contains(wildcardBindingPattern.getOptionalWildcardId().get())) {
          if (scopedHeap.isIdentifierDeclared(wildcardBinding.identifier)) {
            wildcardBinding.logTypeError(
                ClaroTypeException.forIllegalShadowingOfDeclaredVariableForWildcardBinding());
          } else {
            // This is a temporary variable definition that will actually need to get deleted from the symbol table.
            scopedHeap.putIdentifierValue(
                wildcardBinding.identifier,
                wildcardBindingPattern.autoValueIgnored_optionalWildcardBindingType.get().get()
            );
            scopedHeap.initializeIdentifier(wildcardBinding.identifier);
          }
          alreadyHandledWildcardBindings.add(wildcardBindingPattern.getOptionalWildcardId().get());
        }
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
    if (matchedExprType.baseType().equals(BaseType.ONEOF)) {
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
    } else {
      ArrayList<ImmutableList<MaybeWildcardPrimitivePattern>> foundStructuredTypePatterns = new ArrayList<>();
      checkUniqueness = e -> {
        if (e instanceof ImmutableList<?>) {
          ImmutableList<MaybeWildcardPrimitivePattern> currFlattenedPattern =
              (ImmutableList<MaybeWildcardPrimitivePattern>) e;
          // Need to exhaustively check this new pattern against all preceding patterns.
          CheckPattern:
          for (ImmutableList<MaybeWildcardPrimitivePattern> precedingPattern : foundStructuredTypePatterns) {
            for (int i = 0; i < currFlattenedPattern.size(); i++) {
              // We need to be careful about how we do equality checking on TypeProviders, to actually resolve the type.
              MaybeWildcardPrimitivePattern precedingPatternVal =
                  maybeResolveTypeProviderPattern(precedingPattern.get(i), scopedHeap);
              MaybeWildcardPrimitivePattern currPatternVal =
                  maybeResolveTypeProviderPattern(currFlattenedPattern.get(i), scopedHeap);
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
    }
    return checkUniqueness;
  }

  private static MaybeWildcardPrimitivePattern maybeResolveTypeProviderPattern(
      MaybeWildcardPrimitivePattern p, ScopedHeap scopedHeap) {
    if (p.getOptionalExpr().isPresent() &&
        p.getOptionalExpr().get() instanceof TypeProvider) {
      return MaybeWildcardPrimitivePattern.forNullableExpr(((TypeProvider) p.getOptionalExpr()
          .get()).resolveType(scopedHeap));
    }
    return p;
  }

  private static void validateCaseExpr(ScopedHeap scopedHeap, Type matchedExprType, Function<Object, Boolean> checkUniqueness, Expr currCaseExpr) throws ClaroTypeException {
    // They should all be of the correct type relative to the matched expr.
    currCaseExpr.assertExpectedExprType(scopedHeap, matchedExprType);

    // Ensure that each literal value is unique.
    if (!checkUniqueness.apply(currCaseExpr)) {
      currCaseExpr.logTypeError(ClaroTypeException.forDuplicateMatchCase());
    }
  }

  // Attempting to implement the algorithm for exhaustiveness checking detailed in the paper "Warnings for pattern
  // matching" (by Luc Maranget) hosted at http://moscova.inria.fr/~maranget/papers/warn/warn007.html
  private static Optional<ImmutableList<Object>> validatePatternsExhaustiveness(
      ImmutableList<Type> flattenedPatternTypes,
      ImmutableList<ImmutableList<Object>> flattenedPatterns,
      int numWildcards,
      ScopedHeap scopedHeap) {
    // Base cases.
    if (flattenedPatterns.size() == 0) {
      // There are no rows to match the remaining wildcards, so return a list of wildcards.
      return Optional.of(
          IntStream.range(0, numWildcards).boxed()
              .map(unused -> MaybeWildcardPrimitivePattern.forNullableExpr(null))
              .collect(ImmutableList.toImmutableList()));
    } else if (numWildcards == 0) {
      return Optional.empty();
    }

    int currColInd = flattenedPatterns.get(0).size() - numWildcards;

    // Collect ∑ (currColPatternElems) according to the paper. ∑ is the set of all patterns instances that appear in the
    // currently considered column.
    ImmutableSet<Object> currColPatternElems =
        flattenedPatterns.stream()
            .map(
                p -> {
                  Object res = p.get(currColInd);
                  if (res instanceof TypeProvider) {
                    return ((TypeProvider) res).resolveType(scopedHeap);
                  }
                  return res;
                })
            // Wildcards do not count as being a part of ∑.
            .filter(e -> !(e instanceof MaybeWildcardPrimitivePattern
                           && !((MaybeWildcardPrimitivePattern) e).getOptionalExpr().isPresent()))
            .collect(ImmutableSet.toImmutableSet());

    // Determine if ∑ (currColPatternElems) is a "complete signature" for the type being considered.
    Type currColType = flattenedPatternTypes.get(currColInd);
    if ((currColType.equals(Types.BOOLEAN)
         && currColPatternElems.equals(ImmutableSet.of(Boolean.TRUE, Boolean.FALSE)))
        ||
        (currColType.baseType().equals(BaseType.ONEOF)
         && currColPatternElems.equals(((Types.OneofType) currColType).getVariantTypes()))) {
      // We've exhaustively matched all possible variants of this oneof type/boolean.
      // Check I(S(c_k, P), n) according to the paper.
      ImmutableList.Builder<ImmutableList<Object>> S_ck_P;
      for (Object elem : currColPatternElems) {
        S_ck_P = ImmutableList.builder();
        for (ImmutableList<Object> currPattern : flattenedPatterns) {
          Object currConsidered = currPattern.get(currColInd);
          if (elem instanceof Type && currConsidered instanceof TypeProvider) {
            currConsidered = ((TypeProvider) currConsidered).resolveType(scopedHeap);
          }
          if (elem.equals(currConsidered)
              || (currConsidered instanceof MaybeWildcardPrimitivePattern
                  && !((MaybeWildcardPrimitivePattern) currConsidered).getOptionalExpr().isPresent())) {
            S_ck_P.add(currPattern);
          }
        }
        Optional<ImmutableList<Object>> currExhaustivenessRes =
            validatePatternsExhaustiveness(flattenedPatternTypes, S_ck_P.build(), numWildcards - 1, scopedHeap);
        if (currExhaustivenessRes.isPresent()) {
          // We found a counter-example! These patterns are not exhaustive!
          return Optional.of(ImmutableList.builder().add(elem).addAll(currExhaustivenessRes.get()).build());
        }
      }
      // We made it through checking everything and never found a counter-example, hence this subset of the patterns are
      // in fact exhaustive!
      return Optional.empty();
    } else {
      // We have definitely not made an exhaustive match, as every other Claro type is implicitly defined to have
      // infinite instances.
      // Check I(D(P), n - 1) according to the paper.
      ImmutableList<ImmutableList<Object>> D_P =
          flattenedPatterns.stream()
              .filter(p -> {
                // We only care about the patterns with a wildcard at the curr col.
                Object currElem = p.get(currColInd);
                return currElem instanceof MaybeWildcardPrimitivePattern
                       && !((MaybeWildcardPrimitivePattern) currElem).getOptionalExpr().isPresent();
              })
              .collect(ImmutableList.toImmutableList());
      Optional<ImmutableList<Object>> currExhaustivenessRes =
          validatePatternsExhaustiveness(flattenedPatternTypes, D_P, numWildcards - 1, scopedHeap);
      return currExhaustivenessRes.map(
          resTail -> {
            Object currColCounterExamplePatternElem;
            if (currColPatternElems.isEmpty()) {
              // Here we're just gonna add a wildcard to the front of whatever pattern we got back from recursion into
              // default matrix.
              currColCounterExamplePatternElem = MaybeWildcardPrimitivePattern.forNullableExpr(null);
            } else {
              // Here we're gonna add a valid concrete instance that's *not already present in this column*.
              currColCounterExamplePatternElem = getCounterExampleElem(currColType, currColPatternElems);
            }
            return ImmutableList.builder()
                .add(currColCounterExamplePatternElem)
                .addAll(resTail)
                .build();
          }
      );
    }
  }

  private static Object getCounterExampleElem(Type currColType, ImmutableSet<Object> currColPatternElems) {
    switch (currColType.baseType()) {
      case BOOLEAN:
        // By definition this means that currColPatternElems has only a single value. Figure out what it is and return
        // the other one.
        if (currColPatternElems.asList().get(0).equals(Boolean.TRUE)) {
          return Boolean.FALSE;
        }
        return Boolean.TRUE;
      case ONEOF:
        // Here there could be multiple different variants represented, just find the first one that isn't.
        return ((Types.OneofType) currColType).getVariantTypes().stream()
            .filter(v -> !currColPatternElems.contains(v))
            .findFirst()
            .get();
      default:
        // Unfortunately, there's no good value to pick for any other type considering that all other types in the
        // language have infinite instances. I could construct some example instance that's not in the
        // currColPatternElems, but it wouldn't be particularly useful since then if the programmer used exactly that
        // recommendation as a match pattern, they'll *definitely* never reach an exhaustive solution. For the above two
        // cases, the user can at least iterate towards an exhaustive match by repeatedly letting the compiler find
        // another counter-example.
        return MaybeWildcardPrimitivePattern.forNullableExpr(null);
    }
  }

  private static StringBuilder reconstructCounterExample(Type matchedExprType, ImmutableList<Object> counterExample) {
    if (!ImmutableSet.of(BaseType.USER_DEFINED_TYPE, BaseType.ONEOF, BaseType.TUPLE, BaseType.STRUCT)
        .contains(matchedExprType.baseType())) {
      // I have no useful error messaging to provide unless there's at least some level of pattern nesting supported.
      return new StringBuilder("case _ -> ...;");
    }
    return new StringBuilder("case ")
        .append(
            reconstructCounterExample(
                matchedExprType, counterExample, new AtomicInteger(0), new StringBuilder()))
        .append(" -> ...;");
  }

  private static StringBuilder reconstructCounterExample(
      Type matchedExprType, ImmutableList<Object> counterExample, AtomicInteger startInd, StringBuilder res) {
    if (startInd.get() >= counterExample.size()) {
      return res;
    }
    switch (matchedExprType.baseType()) {
      case TUPLE:
        res.append("(");
        ImmutableList<Type> elemTypes = ((Types.TupleType) matchedExprType).getValueTypes();
        for (int i = 0; i < elemTypes.size() - 1; i++) {
          reconstructCounterExample(elemTypes.get(i), counterExample, startInd, res);
          res.append(", ");
        }
        reconstructCounterExample(elemTypes.get(elemTypes.size() - 1), counterExample, startInd, res);
        return res.append(")");
      case STRUCT:
        res.append("{");
        ImmutableList<Type> fieldTypes = ((Types.StructType) matchedExprType).getFieldTypes();
        ImmutableList<String> fieldNames = ((Types.StructType) matchedExprType).getFieldNames();
        for (int i = 0; i < fieldTypes.size() - 1; i++) {
          res.append(fieldNames.get(i)).append(" = ");
          reconstructCounterExample(fieldTypes.get(i), counterExample, startInd, res);
          res.append(", ");
        }
        res.append(fieldNames.get(fieldNames.size() - 1)).append(" = ");
        reconstructCounterExample(fieldTypes.get(fieldTypes.size() - 1), counterExample, startInd, res);
        return res.append("}");
      case USER_DEFINED_TYPE:
        res.append(((Types.UserDefinedType) matchedExprType).getTypeName())
            .append("(");
        // Now do a bunch of work to figure out what the wrapped type is. Dear God Claro's representation of these types
        // is such a painful experience.
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
        reconstructCounterExample(validatedWrappedType, counterExample, startInd, res);
        return res.append(")");
      case ONEOF:
        Object counterExamplePatternElem = counterExample.get(startInd.getAndIncrement());
        if (counterExamplePatternElem instanceof MaybeWildcardPrimitivePattern
            && !((MaybeWildcardPrimitivePattern) counterExamplePatternElem).getOptionalExpr().isPresent()) {
          return res.append("_");
        }
        return res.append("_:").append(counterExamplePatternElem);
      default:
        counterExamplePatternElem = counterExample.get(startInd.getAndIncrement());
        if (counterExamplePatternElem instanceof MaybeWildcardPrimitivePattern
            && !((MaybeWildcardPrimitivePattern) counterExamplePatternElem).getOptionalExpr().isPresent()) {
          return res.append("_");
        }
        return res.append(counterExamplePatternElem);
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(new StringBuilder());

    if (this.matchedExprType.baseType().equals(BaseType.ONEOF)) {
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
      boolean hasDefaultCase =
          !((FlattenedTypeMatchPattern) this.cases.get(this.cases.size() - 1).get(0)).getFlattenedPattern().get(0)
              .getOptionalExpr().isPresent();
      this.caseExprLiterals = this.cases.subList(0, this.cases.size() - (hasDefaultCase ? 1 : 0)).stream()
          .map(l ->
                   ((FlattenedTypeMatchPattern) l.get(0)).getFlattenedPattern().get(0)
                       .toImpliedType(scopedHeap).getJavaSourceClaroType())
          .collect(ImmutableList.toImmutableList());
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
    } else {
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
      if (!(this.matchedExprType instanceof ConcreteType)) {
        codegenNestedValueDestructuring(
            this.matchedExprType, new Stack<>(), 0, syntheticMatchedStructuredTypeIdentifier, res);
      } else {
        res.javaSourceBody()
            .append(this.matchedExprType.getJavaSourceType())
            .append(" $v0 = ")
            .append(syntheticMatchedStructuredTypeIdentifier)
            .append(";\n");
      }
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
      codegenDestructuredSequenceMatch(casesStack, 0, codegen, syntheticMatchedStructuredTypeIdentifier, scopedHeap, this.matchId);
      res = codegen.get();
      res.javaSourceBody().append("} // End structured type match. \n");
    }

    return res;
  }

  // This function is used to recursively traverse an arbitrarily nested type to codegen destructuring of its primitive
  // values into variables in a flat scope named like $v0, ..., $vn.
  private static int codegenNestedValueDestructuring(
      Type matchedExprType, Stack<String> path, int count, String matchedValIdentifier, GeneratedJavaSource codegen) {
    AtomicReference<ImmutableList<Type>> elementTypes_OUT_PARAM = new AtomicReference<>();
    AtomicReference<BiFunction<Type, Integer, String>> getElementAccessPattern_OUT_PARAM = new AtomicReference<>();
    getNestedValueDestructuringPathPartCodegen(matchedExprType, elementTypes_OUT_PARAM, getElementAccessPattern_OUT_PARAM);
    ImmutableList<Type> elementTypes = elementTypes_OUT_PARAM.get();
    BiFunction<Type, Integer, String> getElementAccessPattern = getElementAccessPattern_OUT_PARAM.get();

    for (int i = 0; i < elementTypes.size(); i++) {
      Type elemType = elementTypes.get(i);
      path.push(getElementAccessPattern.apply(elemType, i));
      if (elemType instanceof ConcreteType || elemType instanceof Types.OneofType) {
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

  private static void getNestedValueDestructuringPathPartCodegen(
      Type matchedExprType,
      AtomicReference<ImmutableList<Type>> elementTypes_OUT_PARAM,
      AtomicReference<BiFunction<Type, Integer, String>> getElementAccessPattern_OUT_PARAM) {
    switch (matchedExprType.baseType()) {
      case TUPLE:
        elementTypes_OUT_PARAM.set(((Types.TupleType) matchedExprType).getValueTypes());
        getElementAccessPattern_OUT_PARAM.set((type, i) -> "((" + type.getJavaSourceType() + ") %s.getElement(" + i +
                                                           "))");
        break;
      case STRUCT:
        elementTypes_OUT_PARAM.set(((Types.StructType) matchedExprType).getFieldTypes());
        getElementAccessPattern_OUT_PARAM.set((type, i) -> "((" + type.getJavaSourceType() + ") %s.values[" + i + "])");
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
        elementTypes_OUT_PARAM.set(ImmutableList.of(validatedWrappedType));
        getElementAccessPattern_OUT_PARAM.set((type, i) -> "((" + type.getJavaSourceType() + ") %s.wrappedValue)");
        break;
      default:
        throw new RuntimeException("Internal Compiler Error! Should be unreachable. " + matchedExprType);
    }
  }

  private void validateLiteralPatternMatchesExpectedType(Type matchedExprType, Type patternImpliedType) {
    boolean matches;
    if (matchedExprType.baseType().equals(BaseType.ONEOF)) {
      matches = ((Types.OneofType) matchedExprType).getVariantTypes().contains(patternImpliedType);
    } else {
      matches = matchedExprType.equals(patternImpliedType);
    }
    if (!matches) {
      // TODO(steving) This is lazy, I should be able to log this error on the actual offending pattern.
      this.matchedExpr.logTypeError(
          ClaroTypeException.forInvalidPatternMatchingWrongType(matchedExprType, patternImpliedType));
    }
  }

  // This function is used to flatten arbitrarily nested patterns into a flat sequence of patterns so that they can be
  // switched over using general-purpose logic that's not concerned with the particular structuring of the type.
  private ImmutableList<ImmutableList<Object>> flattenNestedPatterns(
      ImmutableList<ImmutableList<Object>> patterns, Type matchedExprType, ScopedHeap scopedHeap) throws ClaroTypeException {
    ImmutableList.Builder<ImmutableList<Object>> flatPatternsStack = ImmutableList.builder();
    for (ImmutableList<Object> pattern : patterns) {
      ImmutableList.Builder<MaybeWildcardPrimitivePattern> currFlattenedPattern = ImmutableList.builder();
      int countErrorsBefore = Expr.typeErrorsFound.size();
      if (pattern.get(0) instanceof MaybeWildcardPrimitivePattern
          && ((MaybeWildcardPrimitivePattern) pattern.get(0)).getOptionalExpr().isPresent()) {
        // Validate that this is actually matching the correct type.
        validateLiteralPatternMatchesExpectedType(
            matchedExprType, ((MaybeWildcardPrimitivePattern) pattern.get(0)).toImpliedType(scopedHeap));
        // This is not a nested value, there's nothing to flatten.
        currFlattenedPattern.add((MaybeWildcardPrimitivePattern) pattern.get(0));
      } else {
        flattenPattern((TypeMatchPattern<Type>) pattern.get(0), matchedExprType, currFlattenedPattern, scopedHeap, new Stack<>());
      }
      if (Expr.typeErrorsFound.size() > countErrorsBefore) {
        Expr.typeErrorsFound.setSize(countErrorsBefore);
        this.matchedExpr.logTypeError(ClaroTypeException.forInvalidPatternMatchingWrongType(
            matchedExprType,
            ((TypeMatchPattern<?>) pattern.get(0)).toImpliedType(scopedHeap)
        ));
      }
      flatPatternsStack.add(
          ImmutableList.of(
              FlattenedTypeMatchPattern.create(
                  currFlattenedPattern.build(), ((TypeMatchPattern<?>) pattern.get(0)).toImpliedType(scopedHeap)),
              pattern.get(1)
          ));
    }
    return flatPatternsStack.build();
  }

  private void flattenPattern(
      TypeMatchPattern<? extends Type> pattern, Type matchedType, ImmutableList.Builder<MaybeWildcardPrimitivePattern> currFlattenedPattern, ScopedHeap scopedHeap, Stack<String> path) {
    // Handle wildcards first.
    if (pattern instanceof MaybeWildcardPrimitivePattern
        && !((MaybeWildcardPrimitivePattern) pattern).getOptionalExpr().isPresent()) {
      // Before recursing into the type, update the wildcard binding's type and destructuring codegen if necessary.
      if (((MaybeWildcardPrimitivePattern) pattern).isWildcardBinding()
          &&
          !((MaybeWildcardPrimitivePattern) pattern).autoValueIgnored_optionalWildcardBindingType.get().isPresent()) {
        // If this is a wildcard binding, then set the type that the wildcard is matching so that the codegen is able to
        // generate a variable of the appropriate type.
        MaybeWildcardPrimitivePattern wildcardBindingPattern = (MaybeWildcardPrimitivePattern) pattern;
        setWildcardBindingCodegen(matchedType, path, wildcardBindingPattern);
        wildcardBindingPattern.autoValueIgnored_optionalWildcardBindingType.set(Optional.of(matchedType));
      }

      if (matchedType instanceof ConcreteType ||
          matchedType instanceof Types.OneofType) { // Wildcard matching a primitive.
        currFlattenedPattern.add((MaybeWildcardPrimitivePattern) pattern);
      } else { // Wildcard matching a nested type.
        AtomicReference<ImmutableList<Type>> elementTypes_OUT_PARAM = new AtomicReference<>();
        getNestedValueDestructuringPathPartCodegen(matchedType, elementTypes_OUT_PARAM, /*unused*/new AtomicReference<>());
        // Now iterate over the types themselves to put a wildcard for every single primitive.
        for (Type elemType : elementTypes_OUT_PARAM.get()) {
          flattenPattern(pattern, elemType, currFlattenedPattern, scopedHeap, path);
        }
      }
      return;
    }

    // Handle nested patterns here.
    AtomicReference<ImmutableList<Type>> patternTypes_OUT_PARAM = new AtomicReference<>();
    AtomicReference<BiFunction<Type, Integer, String>> getElementAccessPattern_OUT_PARAM = new AtomicReference<>();
    getNestedValueDestructuringPathPartCodegen(matchedType, patternTypes_OUT_PARAM, getElementAccessPattern_OUT_PARAM);
    ImmutableList<? extends TypeMatchPattern<?>> patternParts;
    boolean patternMatchesCorrectType;
    if (pattern instanceof TuplePattern) {
      patternParts = ((TuplePattern) pattern).getElementValues();
      patternMatchesCorrectType = matchedType.baseType().equals(BaseType.TUPLE);
    } else if (pattern instanceof StructPattern) {
      patternParts = ((StructPattern) pattern).getFieldPatterns()
          .stream()
          .map(StructFieldPattern::getValue)
          .collect(ImmutableList.toImmutableList());
      patternMatchesCorrectType = matchedType.baseType().equals(BaseType.STRUCT);
    } else if (pattern instanceof UserDefinedTypePattern) {
      patternParts = ImmutableList.of(((UserDefinedTypePattern) pattern).getWrappedValue());
      patternMatchesCorrectType = matchedType.baseType().equals(BaseType.USER_DEFINED_TYPE);
    } else {
      throw new RuntimeException("Internal Compiler Error! Should be unreachable.");
    }
    if (!(patternMatchesCorrectType && patternTypes_OUT_PARAM.get().size() == patternParts.size())) {
      try {
        throw ClaroTypeException.forInvalidPatternMatchingWrongType(matchedType, pattern.toImpliedType(scopedHeap));
      } catch (ClaroTypeException e) {
        this.matchedExpr.logTypeError(e);
      }
    }
    for (int i = 0; i < patternParts.size(); i++) {
      if (patternParts.get(i) instanceof MaybeWildcardPrimitivePattern
          && ((MaybeWildcardPrimitivePattern) patternParts.get(i)).getOptionalExpr().isPresent()) {
        validateLiteralPatternMatchesExpectedType(
            patternTypes_OUT_PARAM.get().get(i),
            ((MaybeWildcardPrimitivePattern) patternParts.get(i)).toImpliedType(scopedHeap)
        );
        currFlattenedPattern.add((MaybeWildcardPrimitivePattern) patternParts.get(i));
        // Special case: oneof-type-literal-matches can optionally have a wildcard binding associated. So we'll want to
        // add the resolved type here to make codegen easy later on.
        if (((MaybeWildcardPrimitivePattern) patternParts.get(i)).isWildcardBinding()) {
          Type wildcardBindingType;
          ((MaybeWildcardPrimitivePattern) patternParts.get(i)).autoValueIgnored_optionalWildcardBindingType
              .set(Optional.ofNullable(
                  wildcardBindingType =
                      ((TypeProvider)
                           ((MaybeWildcardPrimitivePattern) patternParts.get(i)).getOptionalExpr()
                               .get())
                          .resolveType(scopedHeap)));

          path.push("(" + wildcardBindingType.getJavaSourceType() + ") " +
                    getElementAccessPattern_OUT_PARAM.get().apply(patternTypes_OUT_PARAM.get().get(i), i));
          setWildcardBindingCodegen(wildcardBindingType, path, ((MaybeWildcardPrimitivePattern) patternParts.get(i)));
          path.pop();
        }
      } else {
        path.push(getElementAccessPattern_OUT_PARAM.get().apply(patternTypes_OUT_PARAM.get().get(i), i));
        flattenPattern(patternParts.get(i), patternTypes_OUT_PARAM.get()
            .get(i), currFlattenedPattern, scopedHeap, path);
        path.pop();
      }
    }
  }

  private static void setWildcardBindingCodegen(Type matchedType, Stack<String> path, MaybeWildcardPrimitivePattern wildcardBindingPattern) {
    StringBuilder codegen = new StringBuilder()
        .append(matchedType.getJavaSourceType())
        .append(" ")
        .append(wildcardBindingPattern.getOptionalWildcardBinding().get().identifier)
        .append(" = ");
    String destructuredValCodegen = "%s"; // matchedValIdentifier, which I don't know yet at this stage.
    for (String pathComponent : path) {
      // Need to compose the accesses so that the necessary casts are associated correctly.
      destructuredValCodegen = String.format(pathComponent, destructuredValCodegen);
    }
    codegen.append(destructuredValCodegen).append(";\n");
    wildcardBindingPattern.autoValueIgnored_optionalWildcardBindingDestructuringCodegen.set(Optional.of(codegen));
  }

  // This function will be used to perform codegen for any arbitrarily nested structure that's already had its values
  // de-structured into a flat sequence of values represented as variables $v0, ..., $vn. This modelling is very
  // powerful as it allows the codegen for all manners of structured types to be rendered consistently using efficient
  // switching.
  private static void codegenDestructuredSequenceMatch(Stack<ImmutableList<Object>> cases,
                                                       int startInd, AtomicReference<GeneratedJavaSource> res, String matchedValIdentifier, ScopedHeap scopedHeap,
                                                       long matchId) {
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
               &&
               countWildcardFromInd((ImmutableList<MaybeWildcardPrimitivePattern>) cases.peek().get(0), startInd) ==
               wildcardPrefixLen) {
          switchGroup.push(cases.pop());
        }

        Collections.reverse(switchGroup);
        CodegenSwitchGroup(switchGroup, startInd + wildcardPrefixLen, res, matchedValIdentifier, scopedHeap, matchId);
      } else { // Just go straight to codegen on `case _ ->` or `case (..., _, _)`
        // First thing, check if there are any wildcard bindings I need to codegen assignments for.
        HashSet<String> alreadyCodegendWildcardBindings = Sets.newHashSet();
        for (int i = 0; i < ((ImmutableList<?>) top.get(0)).size(); i++) {
          MaybeWildcardPrimitivePattern patternPart =
              ((ImmutableList<MaybeWildcardPrimitivePattern>) top.get(0)).get(i);
          String wildcardBindingName;
          if (patternPart.isWildcardBinding()
              && !alreadyCodegendWildcardBindings.contains(
              wildcardBindingName = patternPart.getOptionalWildcardBinding().get().identifier)) {
            String codegenWildcardBinding = String.format(
                patternPart.autoValueIgnored_optionalWildcardBindingDestructuringCodegen.get().get().toString(),
                matchedValIdentifier
            );
            // Just so that the usage marking works for the exprs in the StmtListNode, initialize this binding.
            scopedHeap.putIdentifierValue(wildcardBindingName, patternPart.autoValueIgnored_optionalWildcardBindingType.get()
                .get());
            scopedHeap.markIdentifierUsed(wildcardBindingName);
            // Do codegen.
            res.get().javaSourceBody().append(codegenWildcardBinding);
            alreadyCodegendWildcardBindings.add(wildcardBindingName);
          }
        }
        res.updateAndGet(
            codegen -> codegen.createMerged(((StmtListNode) top.get(1)).generateJavaSourceOutput(scopedHeap)));
        // This is an unfortunate hack since I don't want to have to figure out how to avoid adding trailing `break`s
        // if they'd happen to be unreachable beyond these `break $MatchN` clauses. I've already validated using javap
        // that all of this gets optimized out of the JVM bytecode in the final class file, so this doesn't actually
        // matter at the end of the day.
        res.get().javaSourceBody().append("if (true) { break $Match").append(matchId).append("; }\n");
      }
    }
  }

  private static void CodegenSwitchGroup(
      Stack<ImmutableList<Object>> switchGroup, int startInd, AtomicReference<GeneratedJavaSource> res,
      String matchedValIdentifier, ScopedHeap scopedHeap, long matchId) {
    MaybeWildcardPrimitivePattern firstGroupCasePattern =
        ((ImmutableList<MaybeWildcardPrimitivePattern>) switchGroup.peek().get(0)).get(startInd);
    Type firstGroupCasePatternImpliedType = firstGroupCasePattern.toImpliedType(scopedHeap);
    if (firstGroupCasePatternImpliedType.equals(Types.BOOLEAN)) {
      res.get().javaSourceBody().append("if ($v").append(startInd);
      if (firstGroupCasePattern.getOptionalExpr().get().equals(Boolean.FALSE)) {
        res.get().javaSourceBody().append(" == false");
      }
    } else if (firstGroupCasePattern.getOptionalExpr().isPresent()
               && firstGroupCasePattern.getOptionalExpr().get() instanceof TypeProvider) {
      // TODO(steving) At some point I can probably gain a tiny optimization by just switching over the type's hashcode
      //   instead of doing a series of if-stmts. To be safe, I should of course first put in a sanity check that for
      //   this current oneof's variants, all hashcodes are *actually* different. In the case that they're not, probably
      //   would be simplest to just maintain this current if-stmt approach (Maybe just for the collisions? i.e.
      //   encoding the dynamic logic of a hashtable lookup in the static codegen..). For now, I don't necessarily want
      //   to commit to this as I'm not 100% sure of all implications relating to what happens if hashcode impls change
      //   over time in relation to already compiled code....since Claro doesn't have a module system yet, I don't want
      //   to pretend that I know those implications in advance.
      res.get().javaSourceBody().append("if (ClaroRuntimeUtilities.getClaroType(")
          .append("$v").append(startInd)
          .append(").equals(")
          .append(firstGroupCasePatternImpliedType.getJavaSourceClaroType())
          .append(")");
    } else {
      res.get()
          .javaSourceBody()
          .append("switch ($v")
          .append(startInd);
    }
    res.get().javaSourceBody().append(") {\n");
    while (!switchGroup.isEmpty()) {
      Optional<Object> currCase =
          ((ImmutableList<MaybeWildcardPrimitivePattern>) switchGroup.peek().get(0)).get(startInd).getOptionalExpr();
      // In order to determine that matched type literals are equal, the TypeProviders need to be resolved to Types.
      if (currCase.get() instanceof TypeProvider) {
        currCase = Optional.of(((TypeProvider) currCase.get()).resolveType(scopedHeap));
      }
      Stack<ImmutableList<Object>> caseGroup = new Stack<>();
      for (ImmutableList<Object> p : switchGroup) {
        Optional<Object> potentialGroupedCase =
            ((ImmutableList<MaybeWildcardPrimitivePattern>) p.get(0)).get(startInd)
                .getOptionalExpr();
        if (currCase.get() instanceof Type) {
          potentialGroupedCase = Optional.of(((TypeProvider) potentialGroupedCase.get()).resolveType(scopedHeap));
        }
        if (potentialGroupedCase.equals(currCase)) {
          caseGroup.add(p);
        }
      }
      switchGroup.removeAll(caseGroup);

      if (firstGroupCasePatternImpliedType.equals(Types.BOOLEAN)) {
        codegenDestructuredSequenceMatch(caseGroup, startInd + 1, res, matchedValIdentifier, scopedHeap, matchId);
        if (firstGroupCasePattern != null && !switchGroup.isEmpty()) {
          res.get().javaSourceBody().append("} else {");
          firstGroupCasePattern = null; // I'm a monster.
        }
      } else if (firstGroupCasePattern.getOptionalExpr().isPresent()
                 && firstGroupCasePattern.getOptionalExpr().get() instanceof TypeProvider) {
        codegenDestructuredSequenceMatch(caseGroup, startInd + 1, res, matchedValIdentifier, scopedHeap, matchId);
        if (!switchGroup.isEmpty()) {
          res.get().javaSourceBody().append("} else if (ClaroRuntimeUtilities.getClaroType(")
              .append("$v").append(startInd)
              .append(").equals(")
              .append(
                  // Peek the top to find the next type we'll do codegen for.
                  ((TypeProvider)
                       ((ImmutableList<MaybeWildcardPrimitivePattern>) switchGroup.peek().get(0))
                           .get(startInd).getOptionalExpr().get())
                      .resolveType(scopedHeap).getJavaSourceClaroType())
              .append(")) {");
        }
      } else {
        res.get()
            .javaSourceBody()
            .append("\tcase ")
            .append(currCase.get() instanceof String
                    ? String.format("\"%s\"", currCase.get())
                    : currCase.get().toString())
            .append(":\n");
        codegenDestructuredSequenceMatch(caseGroup, startInd + 1, res, matchedValIdentifier, scopedHeap, matchId);
        res.get().javaSourceBody().append("break;\n");
      }
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

    private final AtomicReference<Optional<StringBuilder>>
        autoValueIgnored_optionalWildcardBindingDestructuringCodegen = new AtomicReference<>(Optional.empty());
    private final AtomicReference<Optional<Type>> autoValueIgnored_optionalWildcardBindingType =
        new AtomicReference<>(Optional.empty());


    public static MaybeWildcardPrimitivePattern forNullableExpr(Object nullableExpr) {
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
      } else if (nullableExpr instanceof TypeProvider || nullableExpr instanceof Type) {
        return new AutoValue_MatchStmt_MaybeWildcardPrimitivePattern(Optional.of(nullableExpr), Optional.empty(), Optional.empty());
      }
      throw new RuntimeException("Internal Compiler Error: Should be unreachable.");
    }

    public static MaybeWildcardPrimitivePattern forWildcardBinding(IdentifierReferenceTerm wildcardBinding) {
      return new AutoValue_MatchStmt_MaybeWildcardPrimitivePattern(Optional.empty(), Optional.of(wildcardBinding), Optional.of(MaybeWildcardPrimitivePattern.globalWildcardCount++));
    }

    public static MaybeWildcardPrimitivePattern forTypeLiteralWildcardBinding(
        TypeProvider typeProvider, IdentifierReferenceTerm wildcardBinding) {
      return new AutoValue_MatchStmt_MaybeWildcardPrimitivePattern(
          Optional.of(typeProvider),
          Optional.of(wildcardBinding),
          Optional.of(MaybeWildcardPrimitivePattern.globalWildcardCount++)
      );
    }

    @Override
    public Type toImpliedType(ScopedHeap scopedHeap) {
      if (this.getOptionalExpr().isPresent()) {
        Object primitiveLiteral = this.getOptionalExpr().get();
        if (primitiveLiteral instanceof String) {
          return Types.STRING;
        } else if (primitiveLiteral instanceof Integer) {
          return Types.INTEGER;
        } else if (primitiveLiteral instanceof Boolean) {
          return Types.BOOLEAN;
        } else if (primitiveLiteral instanceof TypeProvider) {
          return ((TypeProvider) primitiveLiteral).resolveType(scopedHeap);
        } else {
          throw new RuntimeException("Internal Compiler Error: Should be unreachable.");
        }
      } else {
        return Types.$GenericTypeParam.forTypeParamName("$_" + this.getOptionalWildcardId().get() + "_");
      }
    }

    public boolean isWildcardBinding() {
      return this.getOptionalWildcardBinding().isPresent();
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
