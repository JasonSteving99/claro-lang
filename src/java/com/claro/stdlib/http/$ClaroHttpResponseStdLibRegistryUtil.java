package com.claro.stdlib.http;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.builtins_impls.http.$ClaroHttpResponse;
import com.claro.intermediate_representation.types.impls.builtins_impls.procedures.ClaroFunction;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class $ClaroHttpResponseStdLibRegistryUtil {

  public static ClaroFunction<$ClaroHttpResponse> getOk200HttpResponseForHtml =
      new ClaroFunction<$ClaroHttpResponse>() {
        @Override
        public $ClaroHttpResponse apply(Object... args) {
          return $ClaroHttpResponse.getOk200HttpResponseForHtmlImpl((String) args[0]);
        }

        @Override
        public Type getClaroType() {
          return getOk200HttpResponseForHtmlProcedureType();
        }
      };

  public static Types.ProcedureType.ProcedureWrapper getOk200HttpResponseForHtmlProcedureWrapper =
      ((Types.ProcedureType) getOk200HttpResponseForHtml.getClaroType()).new ProcedureWrapper() {

        @Override
        public Object apply(ImmutableList<Expr> args, ScopedHeap scopedHeap) {
          return getOk200HttpResponseForHtml.apply(args.get(0).generateInterpretedOutput(scopedHeap));
        }
      };

  public static ClaroFunction<$ClaroHttpResponse> getOk200HttpResponseForJson =
      new ClaroFunction<$ClaroHttpResponse>() {
        @Override
        public $ClaroHttpResponse apply(Object... args) {
          return $ClaroHttpResponse.getOk200HttpResponseForJsonImpl((String) args[0]);
        }

        @Override
        public Type getClaroType() {
          return getOk200HttpResponseForJsonProcedureType();
        }
      };

  public static Types.ProcedureType.ProcedureWrapper getOk200HttpResponseForJsonProcedureWrapper =
      ((Types.ProcedureType) getOk200HttpResponseForJson.getClaroType()).new ProcedureWrapper() {

        @Override
        public Object apply(ImmutableList<Expr> args, ScopedHeap scopedHeap) {
          return getOk200HttpResponseForJson.apply(args.get(0).generateInterpretedOutput(scopedHeap));
        }
      };

  public static Type getOk200HttpResponseForHtmlProcedureType() {
    return Types.ProcedureType.FunctionType.forArgsAndReturnTypes(
        ImmutableList.of(Types.STRING),
        Types.HTTP_RESPONSE,
        ImmutableSet.of(),
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

  public static Type getOk200HttpResponseForJsonProcedureType() {
    return Types.ProcedureType.FunctionType.forArgsAndReturnTypes(
        ImmutableList.of(Types.STRING),
        Types.HTTP_RESPONSE,
        ImmutableSet.of(),
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
