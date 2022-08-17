package com.claro.intermediate_representation.expressions.procedures.functions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.ProcedureDefinitionStmt;
import com.claro.intermediate_representation.statements.UsingBlockStmt;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FunctionCallExpr extends Expr {
  public final String name;
  public final ImmutableList<Expr> argExprs;

  public FunctionCallExpr(String name, ImmutableList<Expr> args, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.name = name;
    this.argExprs = args;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Make sure we check this will actually be a valid reference before we allow it.
    Preconditions.checkState(
        scopedHeap.isIdentifierDeclared(this.name),
        "No function <%s> within the current scope!",
        this.name
    );
    Type referencedIdentifierType = scopedHeap.getValidatedIdentifierType(this.name);
    Preconditions.checkState(
        // Include CONSUMER_FUNCTION just so that later we can throw a more specific error for that case.
        ImmutableSet.of(BaseType.FUNCTION, BaseType.PROVIDER_FUNCTION, BaseType.CONSUMER_FUNCTION)
            .contains(referencedIdentifierType.baseType()),
        "Non-function %s %s cannot be called!",
        referencedIdentifierType,
        this.name
    );
    Preconditions.checkState(
        ((Types.ProcedureType) referencedIdentifierType).hasArgs(),
        "%s %s does not take any args, it cannot be called with arguments!",
        referencedIdentifierType,
        this.name
    );
    Preconditions.checkState(
        ((Types.ProcedureType) referencedIdentifierType).hasReturnValue(),
        "%s %s does not return a value, it cannot be used as an expression!",
        referencedIdentifierType,
        this.name
    );

    ImmutableList<Type> definedArgTyps = ((Types.ProcedureType.FunctionType) referencedIdentifierType).getArgTypes();

    // Make sure that we at least do due diligence and first check that we have the right number of args.
    Preconditions.checkState(
        definedArgTyps.size() == this.argExprs.size(),
        "Expected %s args for function %s, but found %s",
        definedArgTyps.size(),
        this.name,
        this.argExprs.size()
    );

    // Validate that all of the given parameter Exprs are of the correct type.
    for (int i = 0; i < this.argExprs.size(); i++) {
      // Java is stupid yet *again*, types are erased, this is certainly an Expr.
      Expr currArgExpr = ((Expr) this.argExprs.get(i));
      currArgExpr.assertExpectedExprType(scopedHeap, definedArgTyps.get(i));
    }

    // Validate that the procedure has been called in a scope that provides the correct bindings.
    // We only care about referencing top-level functions, not any old function (e.g. not lambdas or func refs).
    FunctionCallExpr.validateNeededBindings(this.name, referencedIdentifierType, scopedHeap);

    // If this happens to be a call to a blocking procedure within another procedure definition, we need to
    // propagate the blocking annotation. In service of Claro's goal to provide "Fearless Concurrency" through Graph
    // Procedures, any procedure that can reach a blocking operation is marked as blocking so that we can prevent its
    // usage from Graph Functions.
    InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
        .ifPresent(
            procedureDefinitionStmt -> {
              if (((Types.ProcedureType) referencedIdentifierType).getAnnotatedBlocking()) {
                ((ProcedureDefinitionStmt) procedureDefinitionStmt)
                    .resolvedProcedureType.getIsBlocking().set(true);
              }
            });

    // Now that everything checks out, go ahead and mark the function used to satisfy the compiler checks.
    scopedHeap.markIdentifierUsed(this.name);

    return ((Types.ProcedureType.FunctionType) referencedIdentifierType).getReturnType();
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    // TODO(steving) It would honestly be best to ensure that the "unused" checking ONLY happens in the type-checking
    // TODO(steving) phase, rather than having to be redone over the same code in the javasource code gen phase.
    scopedHeap.markIdentifierUsed(this.name);

    return new StringBuilder(
        String.format(
            "%s.apply(%s)",
            this.name,
            this.argExprs
                .stream()
                .map(expr -> expr.generateJavaSourceBodyOutput(scopedHeap))
                .collect(Collectors.joining(", "))
        )
    );
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((Types.ProcedureType.ProcedureWrapper) scopedHeap.getIdentifierValue(this.name))
        .apply(this.argExprs, scopedHeap);
  }

  public static void validateNeededBindings(String functionName, Type referencedIdentifierType, ScopedHeap scopedHeap)
      throws ClaroTypeException {
    // Validate that the procedure has been called in a scope that provides the correct bindings.
    // We only care about referencing top-level functions, not any old function (e.g. not lambdas or func refs).
    if (scopedHeap.findIdentifierInitializedScopeLevel(functionName).orElse(-1) == 0) {
      if (InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.isPresent()) {
        // Make sure that if this function call is within a ProcedureDefStmt then we actually need to make sure
        // to register function call with that active procedure def stmt instance so that it knows which top-level
        // procedures it needs to check with to accumulate its transitive used injected keys set.
        if (!UsingBlockStmt.currentlyUsedBindings.isEmpty()) {
          // Using-blocks are actually supported from directly within a procedure definition, so if this procedure call
          // is from within a using block, we'd need to filter the set that we add to the ProcedureDefStmt's direct deps.
          ((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
              .get())
              .directTopLevelProcedureDepsToBeFilteredForExplicitUsingBlockKeyBindings
              .merge(
                  functionName,
                  ImmutableSet.copyOf(UsingBlockStmt.currentlyUsedBindings),
                  // It's possible that this same procedure was already used in a different using block so we just need
                  // the union of the currently used keys to get the minimum.
                  (existingUsedBindingsSet, currUsedBindingsSet) ->
                      Sets.union(existingUsedBindingsSet, currUsedBindingsSet)
                          .stream()
                          .collect(ImmutableSet.toImmutableSet())
              );
        } else {
          ((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
              .get())
              .directTopLevelProcedureDepsSet.add(functionName);
        }
      } else {
        // Calls that are not in a ProcedureDefStmt simply need to be validated.
        Set<Key> currNeededBindings =
            ((Types.ProcedureType) scopedHeap.getValidatedIdentifierType(functionName)).getUsedInjectedKeys();
        if (!UsingBlockStmt.currentlyUsedBindings.containsAll(currNeededBindings)) {
          throw ClaroTypeException.forMissingBindings(
              functionName, referencedIdentifierType, Sets.difference(currNeededBindings, UsingBlockStmt.currentlyUsedBindings));
        }
      }
    }
  }
}
