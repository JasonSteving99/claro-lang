package com.claro.stdlib;

import com.claro.ClaroParser;
import com.claro.ClaroParserException;
import com.claro.compiler_backends.ParserUtil;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.ProgramNode;
import com.claro.intermediate_representation.Target;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.builtins_impls.procedures.ClaroConsumerFunction;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

public class Exec {

  public static ClaroConsumerFunction<String> exec = new ClaroConsumerFunction<String>() {
    @Override
    public void apply(Object... args) {
      execImpl((String) args[0]);
    }

    @Override
    public Type getClaroType() {
      return getProcedureType();
    }
  };

  public static Types.ProcedureType.ProcedureWrapper execProcedureWrapper =
      ((Types.ProcedureType) exec.getClaroType()).new ProcedureWrapper() {

        @Override
        public Object apply(ImmutableList<Expr> args, ScopedHeap scopedHeap) {
          exec.apply(args.get(0).generateInterpretedOutput(scopedHeap));
          return null;
        }
      };

  public static void execImpl(String claroString) {
    ClaroParser claroParser = ParserUtil.createParser(claroString, false);
    try {
      ((ProgramNode) claroParser.parse().value).generateTargetOutput(Target.INTERPRETED, StdLibUtil::registerIdentifiers);
    } catch (Exception e) {
      throw new ClaroParserException("Exec failed: ", e);
    }
  }

  public static String getProcedureName() {
    return "exec";
  }

  public static Type getProcedureType() {
    return Types.ProcedureType.ConsumerType.forConsumerArgTypes(
        ImmutableList.of(Types.STRING),
        Sets.newHashSet(),
        new Stmt(ImmutableList.of()) {
          @Override
          public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
            // Synthetic node, this can't fail.
          }

          @Override
          public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
            return GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
          }

          @Override
          public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
            return null;
          }
        },
        /*explicitlyAnnotatedBlocking=*/false
    );
  }
}
