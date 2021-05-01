package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.BaseType;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.stream.Collectors;

public class FunctionCallExpr extends Expr {
  protected final String name;
  protected final ImmutableList<Expr> argExprs;

  public FunctionCallExpr(String name, ImmutableList<Expr> args) {
    super(ImmutableList.of());
    this.name = name;
    this.argExprs = args;
  }

  @Override
  protected Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
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
        ((Types.ProcedureType) referencedIdentifierType).hasReturnValue(),
        "%s %s does not return a value, it cannot be used as an expression!",
        referencedIdentifierType,
        this.name
    );

    Types.ProcedureType.FunctionType functionType =
        (Types.ProcedureType.FunctionType) scopedHeap.getValidatedIdentifierType(this.name);

    ImmutableList<Type> definedArgTyps = functionType.getArgTypes();

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

    // Now that everything checks out, go ahead and mark the function used to satisfy the compiler checks.
    scopedHeap.markIdentifierUsed(this.name);

    return functionType.getReturnType();
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // TODO(steving) It would honestly be best to ensure that the "unused" checking ONLY happens in the type-checking
    // TODO(steving) phase, rather than having to be redone over the same code in the javasource code gen phase.
    scopedHeap.markIdentifierUsed(this.name);

    return new StringBuilder(
        String.format(
            "%s.apply(%s)",
            this.name,
            this.argExprs
                .stream()
                .map(expr -> expr.generateJavaSourceOutput(scopedHeap))
                .collect(Collectors.joining(", "))
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((Types.ProcedureType.ProcedureWrapper) scopedHeap.getIdentifierValue(this.name))
        .apply(this.argExprs, scopedHeap);
  }
}
