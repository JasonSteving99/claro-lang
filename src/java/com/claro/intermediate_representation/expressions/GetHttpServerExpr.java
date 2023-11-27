package com.claro.intermediate_representation.expressions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.claro.stdlib.StdLibModuleUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class GetHttpServerExpr extends Expr {
  private final Expr portNumber;
  private static final Type GENERIC_PROCEDURE_TYPE =
      Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
          ImmutableList.of(Types.INTEGER),
          Types.HttpServerType.forHttpService(
              // Hack. No need to actually model the generic type param here.
              Types.HttpServiceType.forServiceNameAndDisambiguator("T", "")),
          /*explicitlyAnnotatedBlocking=*/false,
          /*optionalAnnotatedBlockingGenericOverArgs=*/Optional.empty(),
          /*optionalGenericProcedureArgNames=*/Optional.of(ImmutableList.of("T"))
      );
  private Optional<Types.HttpServiceType> assertedHttpService = Optional.empty();

  public GetHttpServerExpr(Expr portNumber, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(ImmutableList.of(), currentLine, currentLineNumber, startCol, endCol);
    this.portNumber = portNumber;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    if (expectedExprType.baseType().equals(BaseType.HTTP_SERVER)) {
      this.assertedHttpService = Optional.of(
          ((Types.HttpServiceType) expectedExprType.parameterizedTypeArgs()
              .get(Types.HttpServerType.HTTP_SERVICE_TYPE)));
    } else {
      this.assertedHttpService = null;
    }

    super.assertExpectedExprType(scopedHeap, expectedExprType);

    StdLibModuleUtil.validateRequiredOptionalStdlibModuleDepIsPresentAndMarkUsedIfSo("http", Optional.of(this));
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (this.assertedHttpService == null) {
      // Some type was asserted but it's not an HttpServer<T> so I can't infer the service to generate a server for.
      return Types.HttpServerType.forHttpService(Types.$GenericTypeParam.forTypeParamName("T"));
    } else if (!this.assertedHttpService.isPresent()) {
      this.logTypeError(
          ClaroTypeException.forGenericProcedureCallWithoutOutputTypeSufficientlyConstrainedByArgsAndContext(
              "getBasicHttpServerForPort", GetHttpServerExpr.GENERIC_PROCEDURE_TYPE));
      return Types.HttpServerType.forHttpService(Types.$GenericTypeParam.forTypeParamName("T"));
    }

    this.portNumber.assertExpectedExprType(scopedHeap, Types.INTEGER);

    // Now I need to finally assert that the requested HttpService has actually had endpoint handlers configured.
    if (!InternalStaticStateUtil.HttpServiceDef_servicesWithValidEndpointHandlersDefined
        .contains(String.format(
            "%s$%s",
            this.assertedHttpService.get().getServiceName(),
            this.assertedHttpService.get().getDefiningModuleDisambiguator()
        ))) {
      this.logTypeError(
          ClaroTypeException.forInvalidHttpServerGenerationRequestedWithNoHttpServiceEndpointHandlersDefined(
              this.assertedHttpService.get().getServiceName(),
              InternalStaticStateUtil.HttpServiceDef_endpointProcedureSignatures.row(
                      this.assertedHttpService.get().getServiceName())
                  .entrySet().stream()
                  .collect(ImmutableMap.toImmutableMap(
                      Map.Entry::getKey,
                      e -> ((Types.ProcedureType) e.getValue()).getArgTypes().size()
                  ))
          ));
    }

    return Types.HttpServerType.forHttpService(
        Types.HttpServiceType.forServiceNameAndDisambiguator(
            this.assertedHttpService.get().getServiceName(),
            this.assertedHttpService.get().getDefiningModuleDisambiguator()
        ));
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(
        new StringBuilder()
            .append("\n\tnew com.claro.runtime_utilities.http.$ClaroHttpServer(\n\t\tcom.claro.runtime_utilities.http.$ClaroHttpServer.getRoutingServlet()"));


    InternalStaticStateUtil.HttpServiceDef_endpointPaths.row(this.assertedHttpService.get().getServiceName())
        .entrySet().stream()
        .map(
            e ->
                String.format(
                    "\n\t\t\t.map(" +
                    "\n\t\t\t\tcom.claro.runtime_utilities.http.$ClaroHttpServer.GET," +
                    "\n\t\t\t\t\"%s\"," +
                    "\n\t\t\t\tcom.claro.runtime_utilities.http.$ClaroHttpServer.getBasicAsyncServlet(\"%s\", httpRequest -> %s$EndpointHandler.apply(%s))" +
                    "\n\t)",
                    e.getValue(),
                    e.getValue(),
                    e.getKey(),
                    e.getValue().contains(":")
                    ? getPathParams(e.getValue()).stream()
                        .map(p -> String.format("httpRequest.getPathParameter(\"%s\")", p))
                        .collect(Collectors.joining(", "))
                    : ""
                ))
        .forEach(res.javaSourceBody()::append);
    res.javaSourceBody()
        .append(",\n\t\tcom.claro.runtime_utilities.http.$ClaroHttpServer.getInetSocketAddressForPort(");
    res = res.createMerged(this.portNumber.generateJavaSourceOutput(scopedHeap));
    res.javaSourceBody().append(")\n\t)");

    return res;
  }

  private static ImmutableList<String> getPathParams(String path) {
    int i = 0;
    ImmutableList.Builder<String> res = ImmutableList.builder();
    while (++i < path.length()) {
      if (path.charAt(i) == ':') {
        int paramStart = ++i;
        while (++i < path.length() && path.charAt(i) != '/') ; // Cute.
        res.add(path.substring(paramStart, i));
      }
    }
    return res.build();
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Eventually need to impl getHttpClient when I come back to adding support for the interpreted backend.
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support `getHttpClient()` in the interpreted backend just yet!");
  }
}
