package com.claro.examples.calculator_example.intermediate_representation;

import com.claro.examples.calculator_example.compiler_backends.interpreted.ScopedHeap;
import com.claro.examples.calculator_example.intermediate_representation.types.BaseType;
import com.claro.examples.calculator_example.intermediate_representation.types.ClaroTypeException;
import com.claro.examples.calculator_example.intermediate_representation.types.Type;
import com.claro.examples.calculator_example.intermediate_representation.types.Types;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.stream.Collectors;

public class ConsumerFunctionCallStmt extends Stmt {
  private final String consumerName;
  private ImmutableMap<String, Expr> argsExprMap;

  // Java is legit so stupid with its type-erasure... args is in fact certainly an ImmutableList<Expr> but of course
  // Java doesn't want to play well with that *eye roll*.
  public ConsumerFunctionCallStmt(String consumerName, ImmutableList<Node> args) {
    super(args);
    this.consumerName = consumerName;
  }

  @Override
  protected void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Make sure we check this will actually be a valid reference before we allow it.
    Preconditions.checkState(
        scopedHeap.isIdentifierDeclared(this.consumerName),
        "No consumer <%s> within the current scope!",
        this.consumerName
    );
    Type referencedIdentifierType = scopedHeap.getValidatedIdentifierType(this.consumerName);
    Preconditions.checkState(
        // Include *_FUNCTION just so that later we can throw a more specific error for that case.
        ImmutableSet.of(BaseType.FUNCTION, BaseType.PROVIDER_FUNCTION, BaseType.CONSUMER_FUNCTION)
            .contains(referencedIdentifierType.baseType()),
        "Non-function %s %s cannot be called!",
        referencedIdentifierType,
        this.consumerName
    );
    Preconditions.checkState(
        !((Types.ProcedureType) referencedIdentifierType).hasReturnValue(),
        "%s %s returns a value, it cannot be used as a statement!",
        referencedIdentifierType,
        this.consumerName
    );

    Types.ProcedureType.ConsumerType consumerType =
        (Types.ProcedureType.ConsumerType) scopedHeap.getValidatedIdentifierType(this.consumerName);

    ImmutableList<Map.Entry<String, Type>> definedArgTyps = consumerType.getArgTypes().entrySet().asList();

    // Make sure that we at least do due diligence and first check that we have the right number of args.
    Preconditions.checkState(
        definedArgTyps.size() == this.getChildren().size(),
        "Expected %s args for function %s, but found %s",
        definedArgTyps.size(),
        this.consumerName,
        this.getChildren().size()
    );

    // Validate that all of the given parameter Exprs are of the correct type.
    ImmutableMap.Builder<String, Expr> argsExprMapBuilder = ImmutableMap.builder();
    for (int i = 0; i < this.getChildren().size(); i++) {
      // Java is stupid yet *again*, types are erased, this is certainly an Expr.
      Expr currArgExpr = ((Expr) this.getChildren().get(i));
      currArgExpr.assertExpectedExprType(scopedHeap, definedArgTyps.get(i).getValue());

      // Since we validated this arg, get ready it ready to pass when actually executing.
      argsExprMapBuilder.put(definedArgTyps.get(i).getKey(), currArgExpr);
    }
    argsExprMap = argsExprMapBuilder.build();

    // Now that everything checks out, go ahead and mark the function used to satisfy the compiler checks.
    scopedHeap.markIdentifierUsed(this.consumerName);
  }

  @Override
  protected StringBuilder generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // TODO(steving) It would honestly be best to ensure that the "unused" checking ONLY happens in the type-checking
    // TODO(steving) phase, rather than having to be redone over the same code in the javasource code gen phase.
    scopedHeap.markIdentifierUsed(this.consumerName);

    return new StringBuilder(
        String.format(
            "%s.apply(%s);\n",
            this.consumerName,
            this.argsExprMap.values()
                .stream()
                .map(expr -> expr.generateJavaSourceOutput(scopedHeap))
                .collect(Collectors.joining(", "))
        )
    );
  }

  @Override
  protected Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((Types.ProcedureType.ProcedureWrapper) scopedHeap.getIdentifierValue(this.consumerName))
        .apply(this.argsExprMap, scopedHeap);
  }
}
