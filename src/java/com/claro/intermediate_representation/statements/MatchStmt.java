package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.UnwrapUserDefinedTypeExpr;
import com.claro.intermediate_representation.expressions.procedures.functions.FunctionCallExpr;
import com.claro.intermediate_representation.expressions.procedures.functions.StructuralConcreteGenericTypeValidationUtil;
import com.claro.intermediate_representation.expressions.term.*;
import com.claro.intermediate_representation.types.*;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class MatchStmt extends Stmt {
  private Expr matchedExpr;
  private final ImmutableList<ImmutableList<Object>> cases;
  private ImmutableList caseExprLiterals;
  private Type matchedExprType;
  private Optional<UnwrapUserDefinedTypeExpr> optionalUnwrapMatchedExpr = Optional.empty();
  private Optional<String> optionalWildcardBindingName = Optional.empty();

  public MatchStmt(Expr matchedExpr, ImmutableList<ImmutableList<Object>> cases) {
    super(ImmutableList.of());
    this.matchedExpr = matchedExpr;
    this.cases = cases;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // TODO(steving) In the future should be able to match over more than just primitives. (Probably everything other
    //   than future<T> since there's no value to match yet?)
    ImmutableSet<Type> supportedMatchTypes = ImmutableSet.of(Types.INTEGER, Types.STRING, Types.BOOLEAN);
    Type matchedExprType = this.matchedExpr.getValidatedExprType(scopedHeap);
    if (!(supportedMatchTypes.contains(matchedExprType)
          || matchedExprType.baseType().equals(BaseType.ONEOF)
          || matchedExprType.baseType().equals(BaseType.USER_DEFINED_TYPE)
    )) {
      this.matchedExpr.logTypeError(ClaroTypeException.forMatchOverUnsupportedType(matchedExprType));
      return;
    }
    Optional<Type> optionalWrappedType = Optional.empty();
    if (matchedExprType.baseType().equals(BaseType.USER_DEFINED_TYPE)) {
      // Make sure it's actually legal to unwrap this user defined type.
      int errorsBefore = Expr.typeErrorsFound.size();

      // Don't want to duplicate the unwrap validation logic, so I'll defer to the Expr node impl.
      Type wrappedType =
          (this.optionalUnwrapMatchedExpr =
               Optional.of(
                   new UnwrapUserDefinedTypeExpr(
                       this.matchedExpr, this.matchedExpr.currentLine, this.matchedExpr.currentLineNumber, this.matchedExpr.startCol, this.matchedExpr.endCol)))
              .get()
              .getValidatedExprType(scopedHeap);

      if (Expr.typeErrorsFound.size() > errorsBefore) {
        // Apparently we've run into some error unwrapping this value, can't continue.
        return;
      }

      // Now I just need to ensure that the wrapped type is something supported.
      if (!supportedMatchTypes.contains(wrappedType)) {
        this.matchedExpr.logTypeError(ClaroTypeException.forMatchOverUnsupportedWrappedType(matchedExprType, wrappedType));
      }
      optionalWrappedType = Optional.of(wrappedType);
    }

    ImmutableList.Builder<String> caseExprLiterals = ImmutableList.builder();
    AtomicReference<Optional<HashSet<Type>>> foundTypeLiterals = new AtomicReference<>(Optional.empty());
    Function<Object, Boolean> checkUniqueness =
        getCheckUniquenessFn(scopedHeap, matchedExprType, caseExprLiterals, foundTypeLiterals);
    // Need to ensure that at the very least our cases look reasonable.
    AtomicBoolean foundDefaultCase = new AtomicBoolean(false);
    for (int i = 0; i < this.cases.size(); i++) {
      ImmutableList<Object> currCase = this.cases.get(i);
      AtomicBoolean narrowingRequired = new AtomicBoolean(false);
      if (currCase.get(0) instanceof ImmutableList) {
        for (Object currCasePattern : (ImmutableList<Object>) currCase.get(0)) {
          validateCasePattern(scopedHeap, matchedExprType, optionalWrappedType, checkUniqueness, foundDefaultCase, currCasePattern, narrowingRequired);
        }
      } else {
        validateCasePattern(scopedHeap, matchedExprType, optionalWrappedType, checkUniqueness, foundDefaultCase, currCase.get(0), narrowingRequired);
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
            narrowedTypeSyntheticIdentifier, ((TypeProvider) currCase.get(0)).resolveType(scopedHeap), null);
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
        if (optionalWrappedType.isPresent()
            && currCase.get(0) instanceof UserDefinedTypePattern
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

  private void validateCasePattern(ScopedHeap scopedHeap, Type matchedExprType, Optional<Type> optionalWrappedType, Function<Object, Boolean> checkUniqueness, AtomicBoolean foundDefaultCase, Object currCasePattern, AtomicBoolean narrowingRequired) throws ClaroTypeException {
    if (currCasePattern instanceof Expr) {
      Expr currCaseExpr = (Expr) currCasePattern;
      validateCaseExpr(scopedHeap, matchedExprType, checkUniqueness, currCaseExpr);
    } else if (currCasePattern instanceof TypeProvider) {
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
      if (pattern.getWrappedValue() instanceof IdentifierReferenceTerm) {
        // The identifier must not already be in use somewhere else, as that shadowing would be confusing.
        String wildcardBinding = ((IdentifierReferenceTerm) pattern.getWrappedValue()).identifier;
        if (scopedHeap.isIdentifierDeclared(wildcardBinding)) {
          pattern.getWrappedValue().logTypeError(
              ClaroTypeException.forIllegalShadowingOfDeclaredVariableForWildcardBinding());
        } else {
          // This is a temporary variable definition that will actually need to get deleted from the symbol table.
          scopedHeap.putIdentifierValue(wildcardBinding, optionalWrappedType.get());
          scopedHeap.initializeIdentifier(wildcardBinding);
          this.optionalWildcardBindingName = Optional.of(wildcardBinding);
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
      new FunctionCallExpr(
          pattern.getTypeName().identifier,
          ImmutableList.of(pattern.getWrappedValue()),
          pattern.getTypeName().currentLine,
          pattern.getTypeName().currentLineNumber,
          pattern.getTypeName().startCol,
          pattern.getTypeName().endCol
      )
          .assertExpectedExprType(scopedHeap, matchedExprType);

      // Ensure that each literal value is unique.
      if (!checkUniqueness.apply(pattern.getWrappedValue())) {
        pattern.getWrappedValue().logTypeError(ClaroTypeException.forDuplicateMatchCase());
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
          if (e instanceof TrueTerm) {
            caseExprLiterals.add("true");
            if ((foundBoolMask[0] & 2) != 0) {
              return false; // Already found it.
            }
            foundBoolMask[0] |= 2;
          } else if (e instanceof FalseTerm) {
            caseExprLiterals.add("false");
            if ((foundBoolMask[0] & 1) != 0) {
              return false; // Already found it.
            }
            foundBoolMask[0] |= 1;
          }
          return true;
        };
        break;
      case INTEGER:
        HashSet<Integer> foundIntLiterals = Sets.newHashSet();
        checkUniqueness = e -> {
          if (e instanceof IntegerTerm) {
            Integer val = ((IntegerTerm) e).value;
            caseExprLiterals.add(val.toString());
            return foundIntLiterals.add(val);
          }
          return true; // Gracefully degrade when we would've already found a type mismatch.
        };
        break;
      case STRING:
        HashSet<String> foundStrLiterals = Sets.newHashSet();
        checkUniqueness = e -> {
          if (e instanceof StringTerm) {
            String val = ((StringTerm) e).getValue();
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
          if (e instanceof TypeProvider) {
            Type val = ((TypeProvider) e).resolveType(scopedHeap);
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
    if (this.optionalUnwrapMatchedExpr.isPresent()) {
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
          if (currCase.get(0) instanceof Expr
              || (currCase.get(0) instanceof UserDefinedTypePattern
                  &&
                  !(usesWildcardBinding =
                        ((UserDefinedTypePattern) currCase.get(0)).getWrappedValue() instanceof IdentifierReferenceTerm))) {
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
                ((IdentifierReferenceTerm) ((UserDefinedTypePattern) currCase.get(0)).getWrappedValue()).identifier;
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

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Eventually need to impl match when I come back to adding support for the interpreted backend.
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support match in the interpreted backend just yet!");
  }

  // This class will be used by the parser to signal that a User-Defined type was matched.
  @AutoValue
  public abstract static class UserDefinedTypePattern {
    public abstract IdentifierReferenceTerm getTypeName();

    public abstract Expr getWrappedValue();

    public static UserDefinedTypePattern forTypeNameAndWrappedValue(IdentifierReferenceTerm typeName, Expr wrappedValue) {
      return new AutoValue_MatchStmt_UserDefinedTypePattern(typeName, wrappedValue);
    }
  }
}
