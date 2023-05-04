package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;

public class SleepStmt extends Stmt {

  private final Expr duration;

  public SleepStmt(Expr duration) {
    super(ImmutableList.of());
    this.duration = duration;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    this.duration.assertExpectedExprType(scopedHeap, Types.INTEGER);

    // In service of Claro's goal to provide "Fearless Concurrency" through Graph Functions, any procedure that can
    // reach a blocking operation is marked as blocking so that we can prevent its usage from Graph Functions.
    InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureDefinitionStmt
        .ifPresent(
            procedureDefinitionStmt ->
                ((ProcedureDefinitionStmt) procedureDefinitionStmt)
                    .resolvedProcedureType.getIsBlocking().set(true));
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource exprGeneratedJavaSource = this.duration.generateJavaSourceOutput(scopedHeap);

    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder(
            String.format(
                "try {\n" +
                "  Thread.sleep((long) %s);\n" +
                "} catch (InterruptedException e) {\n" +
                "  throw new ClaroFuture.Panic(e);\n" +
                "}\n",
                exprGeneratedJavaSource.javaSourceBody()
            )
        )
    );

    res = res.createMerged(
        GeneratedJavaSource.create(
            new StringBuilder(),
            exprGeneratedJavaSource.optionalStaticDefinitions(),
            exprGeneratedJavaSource.optionalStaticPreambleStmts()
        )
    );

    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    try {
      Thread.sleep((long) this.duration.generateInterpretedOutput(scopedHeap));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return null;
  }
}
