package com.claro.intermediate_representation.statements;

import com.claro.ClaroParserException;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.concurrent.atomic.AtomicReference;

public class PipeChainStmt extends Stmt {
  private static int globalPipeChainCount = 0;
  private final int currPipeChainUniqueId;
  private final Expr sourceExpr;
  private final ImmutableList<Object> chainExprs;
  private final Stmt sinkStmt;
  private ImmutableSet<Integer> pipeChainStagesWithMultipleBackreferences;
  private ImmutableList<Type> pipeChainStageTypes;

  public PipeChainStmt(Expr sourceExpr, ImmutableList<Object> chainExprs, Stmt sinkStmt) {
    super(ImmutableList.of());
    this.currPipeChainUniqueId = globalPipeChainCount++;
    this.sourceExpr = sourceExpr;
    this.chainExprs = chainExprs;
    this.sinkStmt = sinkStmt;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    InternalStaticStateUtil.PipeChainStmt_withinPipeChainContext = true;

    // Validate that we have at least one chainExpr. Otherwise, it's more readable to just write a vanilla stmt w/o pipelining.
    Preconditions.checkState(
        this.chainExprs.size() > 0,
        "Invalid Usage of Pipe Chain Statement: Pipe chain statements should only be used when there is" +
        "at least one chain expr between source and sink. Otherwise, you should just instead write a direct stmt.\n" +
        "E.g.:\n\t\tInstead of - \n\t\t\tfoo\n\t\t\t\t-> bar(^);\n\t\tYou should write -\n\t\t\tbar(foo);\n"
    );

    // We need to ensure that all of the types compose together. At this point, the backreferences do not know which
    // expr they're referencing, so we need to manually perform the inside-out type validation. Based on the structural
    // equivalence of a pipe chain stmt with a nesting of composed function calls, we know that iterating the chain top
    // down, is equivalent to recursively parsing inside-out as type checking is normally handled.
    ImmutableSet.Builder<Integer> pipeChainStagesWithMultipleBackreferencesBuilder = ImmutableSet.builder();
    ImmutableList.Builder<Type> pipeChainStageTypesBuilder = ImmutableList.builder();

    // Start off by clearing the prior state setup by any prior PipeChainStmt. Setting this null will enable the
    // PipeChainBackreferenceTerm to know that it was being used incorrectly in the chain source.
    InternalStaticStateUtil.PipeChainStmt_backreferencedPipeChainStageType = null;
    InternalStaticStateUtil.PipeChainStmt_backreferencedPipeChainStageType =
        new AtomicReference<>(sourceExpr.getValidatedExprType(scopedHeap));
    pipeChainStageTypesBuilder.add(InternalStaticStateUtil.PipeChainStmt_backreferencedPipeChainStageType.get());
    Expr backreferencedPipeStageExpr = sourceExpr;
    // Validate all the intermediate chain stages.
    int currPipeStageNum = 1;
    for (Object currChainStage : this.chainExprs) {
      // Reset this count so that we get the count of backreferences used only in the current stage.
      InternalStaticStateUtil.PipeChainStmt_backreferenceUsagesCount = 0;
      // Reset the codegen fn so that we can feed codegen to just the backreference exprs from this curr stage.
      InternalStaticStateUtil.PipeChainStmt_backreferencedPipeChainStageCodegenFn =
          new AtomicReference<>();
      // Validate the current stage based on the previous stage's type, and then update the tracked previous stage type
      // to be the type of the currChainStage so the next stage is ready to validate against it.
      InternalStaticStateUtil.PipeChainStmt_backreferencedPipeChainStageType
          .set(((Expr) currChainStage).getValidatedExprType(scopedHeap));
      pipeChainStageTypesBuilder.add(InternalStaticStateUtil.PipeChainStmt_backreferencedPipeChainStageType.get());

      if (InternalStaticStateUtil.PipeChainStmt_backreferenceUsagesCount < 1) {
        // Verify that each pipe stage contains at least one backreference. Otherwise, we'd declare that data was wasted
        // (a.k.a. implicitly unused).
        ((Expr) currChainStage).logTypeError(
            new IllegalStateException(
                "Invalid Pipe Chain Stage: The current pipe chain stage must use at least one backreference (`^`). " +
                "Not using a backreference in a pipe chain stage represents an implicit waste of unused data."
            )
        );
      } else {
        setBackreferenceCodegenFn(
            scopedHeap, pipeChainStagesWithMultipleBackreferencesBuilder, backreferencedPipeStageExpr, currPipeStageNum);
      }
      currPipeStageNum++;
      backreferencedPipeStageExpr = (Expr) currChainStage;
    }

    // Finally, we're almost done, just need to do one last validation on the sink stmt.}
    InternalStaticStateUtil.PipeChainStmt_backreferenceUsagesCount = 0;
    // Reset the codegen fn so that we can feed codegen to just the backreference exprs from this curr stage.
    InternalStaticStateUtil.PipeChainStmt_backreferencedPipeChainStageCodegenFn =
        new AtomicReference<>();
    // Do type validation of the sink and have sink collect the codegen fn.
    sinkStmt.assertExpectedExprTypes(scopedHeap);

    // TODO(steving) Need some way to emit errors on the Expr in the sink stmt directly, just like on source expr above.
    Preconditions.checkState(
        InternalStaticStateUtil.PipeChainStmt_backreferenceUsagesCount > 0,
        "Invalid Pipe Chain Sink: The pipe chain sink must use at least one backreference (`^`). " +
        "Not using a backreference in a pipe chain stage represents an implicit waste of unused data."
    );
    setBackreferenceCodegenFn(
        scopedHeap, pipeChainStagesWithMultipleBackreferencesBuilder, backreferencedPipeStageExpr, currPipeStageNum);

    this.pipeChainStagesWithMultipleBackreferences = pipeChainStagesWithMultipleBackreferencesBuilder.build();
    this.pipeChainStageTypes = pipeChainStageTypesBuilder.build();

    InternalStaticStateUtil.PipeChainStmt_withinPipeChainContext = false;
  }

  private void setBackreferenceCodegenFn(ScopedHeap scopedHeap, ImmutableSet.Builder<Integer> pipeChainStagesWithMultipleBackreferencesBuilder, Expr backreferencedPipeStageExpr, int currPipeStageNum) {
    if (InternalStaticStateUtil.PipeChainStmt_backreferenceUsagesCount > 1) {
      pipeChainStagesWithMultipleBackreferencesBuilder.add(currPipeStageNum);
      InternalStaticStateUtil.PipeChainStmt_backreferencedPipeChainStageCodegenFn
          .set(
              (unusedCodegenScopedHeap, unusedShouldJavaSourceCodeGen) ->
                  GeneratedJavaSource.forJavaSourceBody(
                      new StringBuilder(
                          getPipeStageTempVarName(
                              this.currPipeChainUniqueId,
                              currPipeStageNum - 1
                          ))));
    } else /*InternalStaticStateUtil.PipeChainStmt_backreferenceUsagesCount == 1*/ {
      InternalStaticStateUtil.PipeChainStmt_backreferencedPipeChainStageCodegenFn
          .set(
              (codegenScopedHeap, shouldJavaSourceCodeGen) ->
                  shouldJavaSourceCodeGen
                  ? backreferencedPipeStageExpr.generateJavaSourceOutput(scopedHeap)
                  : backreferencedPipeStageExpr.generateInterpretedOutput(scopedHeap));
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res;

    // It might turn out the source itself needs a temp var.
    if (pipeChainStagesWithMultipleBackreferences.contains(1)) {
      res = new DeclarationStmt(
          getPipeStageTempVarName(this.currPipeChainUniqueId, 0),
          unused -> pipeChainStageTypes.get(0), sourceExpr
      ).generateJavaSourceOutput(scopedHeap);
      pipeChainStagesWithMultipleBackreferences =
          ImmutableSet.copyOf(pipeChainStagesWithMultipleBackreferences.asList()
                                  .subList(1, pipeChainStagesWithMultipleBackreferences.size()));
    } else {
      res = GeneratedJavaSource.forJavaSourceBody(new StringBuilder(""));
    }

    // Now break up the chain exprs as necessary. Relying on guava collections' stable iteration order here....
    for (int multiBackrefStage : pipeChainStagesWithMultipleBackreferences) {
      res = res.createMerged(
          new DeclarationStmt(
              getPipeStageTempVarName(this.currPipeChainUniqueId, multiBackrefStage - 1),
              unused -> this.pipeChainStageTypes.get(multiBackrefStage - 1),
              (Expr) this.chainExprs.get(multiBackrefStage - 2)
          ).generateJavaSourceOutput(scopedHeap)
      );
    }

    // Now, finalize by blindly doing codegen of the sink.
    res = res.createMerged(sinkStmt.generateJavaSourceOutput(scopedHeap));

    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    throw new ClaroParserException("PipeChainStmt Not yet implemented!");
  }

  private static String getPipeStageTempVarName(int currPipeChainUniqueId, int currStageNum) {
    return String.format(
        "$pipeChain_%s_stage_%s_val",
        currPipeChainUniqueId,
        currStageNum
    );
  }
}
