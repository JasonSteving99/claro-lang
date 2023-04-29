package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.function.Supplier;

public class GetHttpClientExpr extends Expr {
  private final Expr baseUrl;
  private static final Type GENERIC_PROCEDURE_TYPE =
      Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
          ImmutableList.of(Types.STRING),
          Types.HttpClientType.forServiceName("HttpServiceDef"), // Hack. No need to actually model the generic type param here.
          /*explicitlyAnnotatedBlocking=*/false,
          /*optionalAnnotatedBlockingGenericOverArgs=*/Optional.empty(),
          /*optionalGenericProcedureArgNames=*/Optional.of(ImmutableList.of("HttpServiceDef"))
      );
  private Optional<String> assertedHttpServiceName = Optional.empty();

  public GetHttpClientExpr(Expr baseUrl, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.baseUrl = baseUrl;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    if (expectedExprType.baseType().equals(BaseType.HTTP_CLIENT)) {
      this.assertedHttpServiceName = Optional.of(((Types.HttpClientType) expectedExprType).getServiceName());
    }

    super.assertExpectedExprType(scopedHeap, expectedExprType);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (!this.assertedHttpServiceName.isPresent()) {
      this.baseUrl.logTypeError(
          ClaroTypeException.forGenericProcedureCallWithoutOutputTypeSufficientlyConstrainedByArgsAndContext(
              "getHttpClient", GetHttpClientExpr.GENERIC_PROCEDURE_TYPE));
      return Types.UNKNOWABLE;
    }

    this.baseUrl.assertExpectedExprType(scopedHeap, Types.STRING);

    return Types.HttpClientType.forServiceName(this.assertedHttpServiceName.get());
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    return GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder()
            .append("$HttpUtil.getServiceClientForBaseUrl(")
            .append(this.assertedHttpServiceName.get())
            .append(".class, ")
            .append(this.baseUrl.generateJavaSourceOutput(scopedHeap)
                        .javaSourceBody())
            .append(")"));
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Eventually need to impl getHttpClient when I come back to adding support for the interpreted backend.
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support `getHttpClient()` in the interpreted backend just yet!");
  }
}
