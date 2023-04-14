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
import com.google.common.hash.Hashing;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ConsumerFunctionCallStmt extends Stmt {
  protected String consumerName;
  public boolean hashNameForCodegen = false;
  public boolean staticDispatchCodegen = false;
  private final String originalName;
  final protected ImmutableList<Expr> argExprs;
  protected Optional<String> optionalExtraArgsCodegen = Optional.empty();
  Optional<ImmutableList<Type>> optionalConcreteGenericTypeParams = Optional.empty();

  public ConsumerFunctionCallStmt(String consumerName, ImmutableList<Expr> args) {
    super(ImmutableList.of());
    this.consumerName = consumerName;
    this.originalName = consumerName;
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

    if (consumerType.getAnnotatedBlocking() == null
        // If the procedure being called is both generic and blocking-generic, then let's handle that all at once
        // in the generic case.
        // TODO(steving) At some point refactor away the special case for only being blocking-generic.
        && !consumerType.getGenericProcedureArgNames().isPresent()) {
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
    } else if (consumerType.getGenericProcedureArgNames().isPresent()) {
      // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
      // !WARNING! CONSIDER THE ENTIRE BELOW SECTION AS CONCEPTUALLY A SINGLE INLINED FUNCTION CALL. EDIT AS A UNIT.
      //
      // This hackery is a workaround for Java not supporting true reference semantics to allowing variables to be
      // updated by called functions, and also not allowing painless multiple returns. So these AtomicReferences are
      // used as a workaround to simulate some sort of "out params". All of this only exists because I need this exact
      // same behavior in the implementations of FunctionCallExpr/ProviderFunctionCallExpr/ConsumerFunctionCallStmt.
      // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
      AtomicReference<Types.ProcedureType> referencedIdentifierType_OUT_PARAM =
          new AtomicReference<>((Types.ProcedureType) referencedIdentifierType);
      AtomicReference<String> procedureName_OUT_PARAM = new AtomicReference<>(this.consumerName);
      AtomicReference<Optional<ImmutableList<Type>>> optionalConcreteGenericTypeParams_OUT_PARAM =
          new AtomicReference<>(this.optionalConcreteGenericTypeParams);
      try {
        FunctionCallExpr.validateGenericProcedureCall(
            /*optionalThisExprWithReturnValue=*/ Optional.empty(),
            /*assertedOutputTypeForGenericFunctionCallUse=*/ Optional.empty(),
                                                 this.argExprs,
                                                 scopedHeap,
                                                 // "Out params".
            /*calledFunctionReturnType_OUT_PARAM=*/ new AtomicReference<>(null),
                                                 referencedIdentifierType_OUT_PARAM,
                                                 procedureName_OUT_PARAM,
                                                 optionalConcreteGenericTypeParams_OUT_PARAM
        );
      } finally {
        // Accumulate side effects from the call above regardless of whether it ended up throwing some exception.
        referencedIdentifierType = referencedIdentifierType_OUT_PARAM.get();
        this.consumerName = procedureName_OUT_PARAM.get();
        this.optionalConcreteGenericTypeParams = optionalConcreteGenericTypeParams_OUT_PARAM.get();
      }
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
    // Pass in the original func name called so that for the case of monomorphized generic funcs we have a single
    // source of truth.
    FunctionCallExpr.validateNeededBindings(this.originalName, referencedIdentifierType, scopedHeap);

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
    // It's possible that during the process of monomorphization when we are doing type checking over a particular
    // signature, this function call might represent the identification of a new signature for a generic function that
    // needs monomorphization. In that case, this function's identifier may not be in the scoped heap yet and that's ok.
    if (!this.consumerName.contains("$MONOMORPHIZATION")) {
      scopedHeap.markIdentifierUsed(this.consumerName);
    } else {
      this.hashNameForCodegen = true;
    }

    if (this.hashNameForCodegen) {
      // In order to call the actual monomorphization, we need to ensure that the name isn't too long for Java.
      // So, we're following a hack where all monomorphization names are sha256 hashed to keep them short while
      // still unique.
      this.consumerName =
          String.format(
              "%s__%s",
              this.originalName,
              Hashing.sha256().hashUnencodedChars(this.consumerName).toString()
          );
    }

    AtomicReference<GeneratedJavaSource> argValsGenJavaSource =
        new AtomicReference<>(GeneratedJavaSource.forJavaSourceBody(new StringBuilder()));

    GeneratedJavaSource consumerFnGenJavaSource =
        GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder(
                String.format(
                    this.staticDispatchCodegen ? "%s(%s%s);\n" : "%s.apply(%s%s);\n",
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
                        .collect(Collectors.joining(", ")),
                    this.staticDispatchCodegen && this.optionalExtraArgsCodegen.isPresent()
                    ? ", " + this.optionalExtraArgsCodegen.get()
                    : ""
                )
            )
        );

    // This node will be potentially reused assuming that it is called within a Generic function that gets
    // monomorphized as that process will reuse the exact same nodes over multiple sets of types. So reset
    // the name now.
    this.consumerName = this.originalName;

    return consumerFnGenJavaSource.createMerged(argValsGenJavaSource.get());
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((Types.ProcedureType.ProcedureWrapper) scopedHeap.getIdentifierValue(this.consumerName))
        .apply(this.argExprs, scopedHeap);
  }
}
