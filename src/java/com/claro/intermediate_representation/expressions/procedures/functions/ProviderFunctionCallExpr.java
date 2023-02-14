package com.claro.intermediate_representation.expressions.procedures.functions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.ProcedureDefinitionStmt;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;

import java.util.function.Supplier;

public class ProviderFunctionCallExpr extends Expr {
  protected String functionName;
  private final String originalName;
  protected boolean hashNameForCodegen;

  public ProviderFunctionCallExpr(String functionName, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.functionName = functionName;
    this.originalName = functionName;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Make sure we check this will actually be a valid reference before we allow it.
    Preconditions.checkState(
        scopedHeap.isIdentifierDeclared(this.functionName),
        "No function <%s> within the current scope!",
        this.functionName
    );
    Type referencedIdentifierType = scopedHeap.getValidatedIdentifierType(this.functionName);
    Preconditions.checkState(
        // Include *_FUNCTION just so that later we can throw a more specific error for that case.
        ImmutableSet.of(BaseType.FUNCTION, BaseType.PROVIDER_FUNCTION, BaseType.CONSUMER_FUNCTION)
            .contains(referencedIdentifierType.baseType()),
        "Non-function %s %s cannot be called!",
        referencedIdentifierType,
        this.functionName
    );
    Preconditions.checkState(
        ((Types.ProcedureType) referencedIdentifierType).hasReturnValue(),
        "%s %s does not return a value, it cannot be used as an expression!",
        referencedIdentifierType,
        this.functionName
    );
    Preconditions.checkState(
        !((Types.ProcedureType) referencedIdentifierType).hasArgs(),
        "Expected %s args for function %s but none given!",
        ((Types.ProcedureType) referencedIdentifierType).getArgTypes().size(),
        this.functionName
    );

    // Validate that the procedure has been called in a scope that provides the correct bindings.
    // We only care about referencing top-level functions, not any old function (e.g. not lambdas or func refs).
    // Pass in the original func name called so that for the case of monomorphized generic funcs we have a single
    // source of truth.
    FunctionCallExpr.validateNeededBindings(this.originalName, referencedIdentifierType, scopedHeap);

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
    scopedHeap.markIdentifierUsed(this.functionName);

    return ((Types.ProcedureType.ProviderType) referencedIdentifierType).getReturnType();
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    // TODO(steving) It would honestly be best to ensure that the "unused" checking ONLY happens in the type-checking
    // TODO(steving) phase, rather than having to be redone over the same code in the javasource code gen phase.
    // It's possible that during the process of monomorphization when we are doing type checking over a particular
    // signature, this function call might represent the identification of a new signature for a generic function that
    // needs monomorphization. In that case, this function's identifier may not be in the scoped heap yet and that's ok.
    if (!this.functionName.contains("$MONOMORPHIZATION")) {
      scopedHeap.markIdentifierUsed(this.functionName);
    } else {
      this.hashNameForCodegen = true;
    }

    if (this.hashNameForCodegen) {
      // In order to call the actual monomorphization, we need to ensure that the name isn't too long for Java.
      // So, we're following a hack where all monomorphization names are sha256 hashed to keep them short while
      // still unique.
      this.functionName =
          String.format(
              "%s__%s",
              this.originalName,
              Hashing.sha256().hashUnencodedChars(this.functionName).toString()
          );
    }

    StringBuilder res = new StringBuilder(String.format("%s.apply()", this.functionName));

    // This node will be potentially reused assuming that it is called within a Generic function that gets
    // monomorphized as that process will reuse the exact same nodes over multiple sets of types. So reset
    // the name now.
    this.functionName = this.originalName;

    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    return ((Types.ProcedureType.ProcedureWrapper) scopedHeap.getIdentifierValue(this.functionName))
        .apply(scopedHeap);
  }
}
