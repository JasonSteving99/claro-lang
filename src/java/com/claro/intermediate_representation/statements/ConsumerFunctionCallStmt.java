package com.claro.intermediate_representation.statements;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.procedures.functions.FunctionCallExpr;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ConsumerFunctionCallStmt extends Stmt {
  private final String consumerName;
  private ImmutableList<Expr> argExprs;

  public ConsumerFunctionCallStmt(String consumerName, ImmutableList<Expr> args) {
    super(ImmutableList.of());
    this.consumerName = consumerName;
    this.argExprs = args;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
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

    Types.ProcedureType.ConsumerType consumerType = (Types.ProcedureType.ConsumerType) referencedIdentifierType;
    ImmutableList<Type> definedArgTypes = consumerType.getArgTypes();
    int argsCount = definedArgTypes.size();

    // Make sure that we at least do due diligence and first check that we have the right number of args.
    Preconditions.checkState(
        argsCount == this.argExprs.size(),
        "Expected %s args for function %s, but found %s",
        argsCount,
        this.consumerName,
        this.argExprs.size()
    );

    if (consumerType.getAnnotatedBlocking() == null) {
      // This function must be generic over the blocking keyword, so need to see if the call is targeting a concrete
      // type signature for this function.
      ImmutableSet<Integer> blockingGenericArgIndices =
          consumerType.getAnnotatedBlockingGenericOverArgs().get();

      // We need to accept whatever inferred type given by the args we're deriving the blocking annotation from.
      // For the rest, we'll assert the known required types.
      boolean isBlocking = false;
      for (int i = 0; i < argsCount; i++) {
        if (blockingGenericArgIndices.contains(i)) {
          // Generically attempting to accept a concrete blocking variant that the user gave for this arg.
          Types.ProcedureType maybeBlockingArgType = (Types.ProcedureType) definedArgTypes.get(i);
          Type concreteArgType;
          switch (maybeBlockingArgType.baseType()) {
            case FUNCTION:
              concreteArgType = argExprs.get(i).assertSupportedExprType(
                  scopedHeap,
                  ImmutableSet.of(
                      Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
                          maybeBlockingArgType.getArgTypes(),
                          maybeBlockingArgType.getReturnType(),
                          true
                      ),
                      Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
                          maybeBlockingArgType.getArgTypes(),
                          maybeBlockingArgType.getReturnType(),
                          false
                      )
                  )
              );
              break;
            case CONSUMER_FUNCTION:
              concreteArgType = argExprs.get(i).assertSupportedExprType(
                  scopedHeap,
                  ImmutableSet.of(
                      Types.ProcedureType.ConsumerType.typeLiteralForConsumerArgTypes(
                          maybeBlockingArgType.getArgTypes(), true),
                      Types.ProcedureType.ConsumerType.typeLiteralForConsumerArgTypes(
                          maybeBlockingArgType.getArgTypes(), false)
                  )
              );
              break;
            case PROVIDER_FUNCTION:
              concreteArgType = argExprs.get(i).assertSupportedExprType(
                  scopedHeap,
                  ImmutableSet.of(
                      Types.ProcedureType.ProviderType.typeLiteralForReturnType(
                          maybeBlockingArgType.getReturnType(), true),
                      Types.ProcedureType.ProviderType.typeLiteralForReturnType(
                          maybeBlockingArgType.getReturnType(), false)
                  )
              );
              break;
            default:
              throw new ClaroParserException("Internal Compiler Error: Grammar allowed a non-procedure type to be annotated blocking!");
          }
          // We'll determine the hard rule, that if even a single of the blocking-generic-args are blocking, then
          // the entire function call is blocking.
          isBlocking = ((Types.ProcedureType) concreteArgType).getAnnotatedBlocking();
        } else {
          // This arg is not being treated as generic, so assert against the static defined type.
          argExprs.get(i).assertExpectedExprType(scopedHeap, definedArgTypes.get(i));
        }
      }

      // From now, we'll stop validating against the generic type signature, and validate against this concrete
      // signature since we know which one we want to use to continue type-checking with now. (Note, this is a
      // bit of a white lie - the blocking concrete variant signature has *all* blocking for the blocking-generic
      // args, which is not necessarily the case for the *actual* args, but this doesn't matter since after this
      // point we're actually done with the args type checking, and we'll move onto codegen where importantly the
      // generated code is all exactly the same across blocking/non-blocking).
      referencedIdentifierType =
          scopedHeap.getValidatedIdentifierType(
              (isBlocking ? "$blockingConcreteVariant_" : "$nonBlockingConcreteVariant_")
              // Refer to the name of the procedure as defined by the owning ProcedureDefinitionStmt rather
              + ((ProcedureDefinitionStmt)
                     ((Types.ProcedureType.ConsumerType) referencedIdentifierType)
                         .getProcedureDefStmt()).procedureName);
    } else {
      // Validate that all of the given parameter Exprs are of the correct type.
      for (int i = 0; i < this.argExprs.size(); i++) {
        // Java is stupid yet *again*, types are erased, this is certainly an Expr.
        Expr currArgExpr = ((Expr) this.argExprs.get(i));
        currArgExpr.assertExpectedExprType(scopedHeap, definedArgTypes.get(i));
      }
    }

    // Validate that the procedure has been called in a scope that provides the correct bindings.
    // We only care about referencing top-level functions, not any old function (e.g. not lambdas or func refs).
    FunctionCallExpr.validateNeededBindings(this.consumerName, referencedIdentifierType, scopedHeap);

    // If this happens to be a call to a blocking procedure within another procedure definition, we need to
    // propagate the blocking annotation. In service of Claro's goal to provide "Fearless Concurrency" through Graph
    // Procedures, any procedure that can reach a blocking operation is marked as blocking so that we can prevent its
    // usage from Graph Functions.
    if (InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt.isPresent()) {
      if (((Types.ProcedureType) referencedIdentifierType).getAnnotatedBlocking()) {
        ((ProcedureDefinitionStmt) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
            .get())
            .resolvedProcedureType.getIsBlocking().set(true);
      }
    }

    // Now that everything checks out, go ahead and mark the function used to satisfy the compiler checks.
    scopedHeap.markIdentifierUsed(this.consumerName);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // TODO(steving) It would honestly be best to ensure that the "unused" checking ONLY happens in the type-checking
    // TODO(steving) phase, rather than having to be redone over the same code in the javasource code gen phase.
    scopedHeap.markIdentifierUsed(this.consumerName);

    AtomicReference<GeneratedJavaSource> argValsGenJavaSource =
        new AtomicReference<>(GeneratedJavaSource.forJavaSourceBody(new StringBuilder()));

    GeneratedJavaSource consumerFnGenJavaSource =
        GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder(
                String.format(
                    "%s.apply(%s);\n",
                    this.consumerName,
                    this.argExprs
                        .stream()
                        .map(expr -> {
                          GeneratedJavaSource currArgGenJavaSource = expr.generateJavaSourceOutput(scopedHeap);
                          String currArgJavaSourceBody = currArgGenJavaSource.javaSourceBody().toString();
                          // We've already consumed the javaSourceBody, so it's safe to clear.
                          currArgGenJavaSource.javaSourceBody().setLength(0);
                          argValsGenJavaSource.set(argValsGenJavaSource.get().createMerged(currArgGenJavaSource));
                          return currArgJavaSourceBody;
                        })
                        .collect(Collectors.joining(", "))
                )
            )
        );

    return consumerFnGenJavaSource.createMerged(argValsGenJavaSource.get());
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((Types.ProcedureType.ProcedureWrapper) scopedHeap.getIdentifierValue(this.consumerName))
        .apply(this.argExprs, scopedHeap);
  }
}
