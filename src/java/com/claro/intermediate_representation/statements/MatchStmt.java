package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.term.*;
import com.claro.intermediate_representation.types.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.HashSet;
import java.util.Optional;
import java.util.function.Function;

public class MatchStmt extends Stmt {
  private final Expr matchedExpr;
  private final ImmutableList<ImmutableList<Object>> cases;
  private ImmutableList caseExprLiterals;
  private Type matchedExprType;

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
    if (!(supportedMatchTypes.contains(matchedExprType) || matchedExprType.baseType().equals(BaseType.ONEOF))) {
      this.matchedExpr.logTypeError(ClaroTypeException.forMatchOverUnsupportedType(matchedExprType));
      return;
    }

    ImmutableList.Builder<String> caseExprLiterals = ImmutableList.builder();
    Optional<HashSet<Type>> foundTypeLiterals = Optional.empty();
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
        foundTypeLiterals = Optional.of(Sets.newHashSet());
        Optional<HashSet<Type>> finalFoundTypeLiterals = foundTypeLiterals;
        checkUniqueness = e -> {
          if (e instanceof TypeProvider) {
            Type val = ((TypeProvider) e).resolveType(scopedHeap);
            caseExprLiterals.add(val.getJavaSourceClaroType());
            return finalFoundTypeLiterals.get().add(val);
          }
          return true; // Gracefully degrade when we would've already found a type mismatch.
        };
        break;
      default:
        throw new RuntimeException("Internal Compiler Error! Should be unreachable.");
    }
    // Need to ensure that at the very least our cases look reasonable.
    boolean foundDefaultCase = false;
    for (int i = 0; i < this.cases.size(); i++) {
      ImmutableList<Object> currCase = this.cases.get(i);
      boolean narrowingRequired = false;
      if (currCase.get(0) instanceof Expr) {
        Expr currCaseExpr = (Expr) currCase.get(0);
        validateCaseExpr(scopedHeap, matchedExprType, checkUniqueness, currCaseExpr);
      } else if (currCase.get(0) instanceof ImmutableList) {
        for (Expr currCaseExpr : (ImmutableList<Expr>) currCase.get(0)) {
          validateCaseExpr(scopedHeap, matchedExprType, checkUniqueness, currCaseExpr);
        }
      } else if (currCase.get(0) instanceof TypeProvider) {
        // Ensure that each literal value is unique.
        if (!checkUniqueness.apply(currCase.get(0))) {
          // TODO(steving) This is lazy, I should be able to log this error on the actual offending type literal.
          this.matchedExpr.logTypeError(ClaroTypeException.forDuplicateMatchCase());
        }
        if (!matchedExprType.baseType().equals(BaseType.ONEOF)) {
          // I'm not going to allow type literals as patterns for non-oneof values as it's a useless match.
          this.matchedExpr.logTypeError(ClaroTypeException.forUselessMatchCaseTypeLiteralPatternForNonOneofMatchedVal(matchedExprType));
        }
        // Handle narrowing if possible.
        if (this.matchedExpr instanceof IdentifierReferenceTerm) {
          narrowingRequired = true;
        }
      } else {
        if (foundDefaultCase) {
          // TODO(steving) This is lazy, I should be able to log this error on the actual offending `_`.
          this.matchedExpr.logTypeError(ClaroTypeException.forMatchContainsDuplicateDefaultCases());
          continue;
        }
        foundDefaultCase = true;
      }

      // Each branch gets isolated to its own scope.
      scopedHeap.observeNewScope(/*beginIdentifierInitializationBranchInspection=*/true);
      // Handle narrowing if required.
      Optional<Type> originalIdentifierTypeMarkedNarrowed = Optional.empty();
      if (narrowingRequired) {
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
      if (narrowingRequired) {
        originalIdentifierTypeMarkedNarrowed.get().autoValueIgnored_IsNarrowedType.set(false);
      }
      // After observing all scopes in this exhaustive match, then finalize the branch inspection.
      scopedHeap.exitCurrObservedScope(/*finalizeIdentifierInitializationBranchInspection=*/i == this.cases.size() - 1);
    }

    if (matchedExprType.baseType().equals(BaseType.BOOLEAN)) {
      if (!foundDefaultCase && this.cases.size() < 2) {
        // Ugly hack means that errors for duplicate branches and exhaustiveness will show up on separate compiles...not sure if I like this.
        this.matchedExpr.logTypeError(ClaroTypeException.forMatchIsNotExhaustiveOverAllPossibleValues(matchedExprType));
      }
      // It's useless to have a default case when the other branches are already exhaustively matching the value.
      if (foundDefaultCase && this.cases.size() > 2) {
        // TODO(steving) This is lazy, I should be able to log this error on the actual offending `_`.
        this.matchedExpr.logTypeError(ClaroTypeException.forUselessDefaultCaseInAlreadyExhaustiveMatch());
      }
    } else if (!foundDefaultCase &&
               !(foundTypeLiterals.isPresent()
                 && foundTypeLiterals.get().containsAll(((Types.OneofType) matchedExprType).getVariantTypes()))) {
      // TODO(steving) This is an ugly error, it shouldn't point at the matched expr, it should point at the `match` keyword.
      this.matchedExpr.logTypeError(ClaroTypeException.forMatchIsNotExhaustiveOverAllPossibleValues(matchedExprType));
    }

    // It's useless to use a match statement to just simply match a wildcard.
    if (foundDefaultCase && this.cases.size() < 2) {
      // TODO(steving) This is an ugly error, it shouldn't point at the matched expr, it should point at the `match` keyword.
      this.matchedExpr.logTypeError(ClaroTypeException.forUselessMatchStatementOverSingleDefaultCase());
    }
    // It's useless to have a default case when the other branches are already exhaustively matching the value.
    if (matchedExprType.baseType().equals(BaseType.ONEOF)
        && foundDefaultCase
        && foundTypeLiterals.isPresent()
        && foundTypeLiterals.get().containsAll(((Types.OneofType) matchedExprType).getVariantTypes())) {
      // TODO(steving) This is lazy, I should be able to log this error on the actual offending `_`.
      this.matchedExpr.logTypeError(ClaroTypeException.forUselessDefaultCaseInAlreadyExhaustiveMatch());
    }

    // Preserve information needed for codegen.
    this.caseExprLiterals = caseExprLiterals.build();
    this.matchedExprType = matchedExprType;
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

    // Matches of INTEGER/STRING can simply be congen'd as a Java switch.
    switch (this.matchedExprType.baseType()) {
      case INTEGER:
      case STRING:
        res.javaSourceBody().append("switch (");
        res = res.createMerged(this.matchedExpr.generateJavaSourceOutput(scopedHeap));
        res.javaSourceBody().append(") {\n");
        for (int i = 0, caseLiteralsInd = 0; i < this.cases.size(); i++, caseLiteralsInd++) {
          ImmutableList<Object> currCase = this.cases.get(i);
          if (currCase.get(0) instanceof Expr) {
            res.javaSourceBody()
                .append("\tcase ")
                .append(this.caseExprLiterals.get(caseLiteralsInd))
                .append(":\n");
          } else if (currCase.get(0) instanceof ImmutableList) { // Must be a multi-match case.
            for (Expr currCaseExpr : (ImmutableList<Expr>) currCase.get(0)) {
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

    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Eventually need to impl match when I come back to adding support for the interpreted backend.
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support match in the interpreted backend just yet!");
  }
}
