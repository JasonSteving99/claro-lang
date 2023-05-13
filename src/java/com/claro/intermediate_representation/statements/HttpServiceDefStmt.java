package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.FormatStringExpr;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.*;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

public class HttpServiceDefStmt extends Stmt {
  private final IdentifierReferenceTerm serviceName;
  private final ImmutableMap<IdentifierReferenceTerm, Object> endpoints;
  private ArrayList<ProcedureDefinitionStmt> syntheticEndpointProcedures = new ArrayList<>();

  public HttpServiceDefStmt(IdentifierReferenceTerm serviceName, ImmutableMap<IdentifierReferenceTerm, Object> endpoints) {
    super(ImmutableList.of());
    this.serviceName = serviceName;
    this.endpoints = endpoints;
  }

  public void registerHttpProcedureTypeProviders(ScopedHeap scopedHeap) {
    BiFunction<ImmutableList<Expr>, String, StmtListNode> syntheticHttpProcStmtList =
        (argNames, endpointName) ->
            new StmtListNode(
                new ReturnStmt(
                    new Expr(ImmutableList.of(), () -> "", -1, -1, -1) {
                      @Override
                      public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
                        scopedHeap.markIdentifierUsed("$httpClient");
                        // By now we know that these have been asserted to be IdentiferReferenceTerms.
                        argNames.stream()
                            .map(e -> ((IdentifierReferenceTerm) e).identifier)
                            .forEach(scopedHeap::markIdentifierUsed);
                        return Types.FutureType.wrapping(
                            Types.OneofType.forVariantTypes(
                                ImmutableList.of(
                                    Types.STRING,
                                    Types.UserDefinedType.forTypeNameAndParameterizedTypes(
                                        "Error", ImmutableList.of(Types.STRING))
                                )));
                      }

                      @Override
                      public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
                        // Just mark everything used again.
                        scopedHeap.markIdentifierUsed("$httpClient");
                        argNames.stream()
                            .map(e -> ((IdentifierReferenceTerm) e).identifier)
                            .forEach(scopedHeap::markIdentifierUsed);

                        GeneratedJavaSource res =
                            GeneratedJavaSource.forJavaSourceBody(
                                new StringBuilder("$HttpUtil.executeAsyncHttpRequest($httpClient.")
                                    .append(endpointName).append("("));
                        if (!argNames.isEmpty()) {
                          argNames.subList(0, argNames.size() - 1).forEach(
                              arg ->
                                  res.javaSourceBody().append(((IdentifierReferenceTerm) arg).identifier).append(", "));
                          res.javaSourceBody()
                              .append(((IdentifierReferenceTerm) argNames.get(argNames.size() - 1)).identifier);
                        }
                        res.javaSourceBody().append("))");
                        return res;
                      }

                      @Override
                      public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
                        throw new RuntimeException("Internal Compiler Error: This should be unreachable!");
                      }
                    },
                    new AtomicReference<>(
                        TypeProvider.ImmediateTypeProvider.of(
                            Types.FutureType.wrapping(
                                Types.OneofType.forVariantTypes(
                                    ImmutableList.of(
                                        Types.STRING,
                                        Types.UserDefinedType.forTypeNameAndParameterizedTypes(
                                            "Error", ImmutableList.of(Types.STRING))
                                    )))))
                ));

    for (Map.Entry<IdentifierReferenceTerm, Object> endpoint : this.endpoints.entrySet()) {
      if (scopedHeap.isIdentifierDeclared(endpoint.getKey().identifier)) {
        // TODO(steving) Long term I most likely don't want these to end up being top-level identifiers, but for now it'll
        //   be the best I can do until I have proper namespacing support.
        endpoint.getKey().logTypeError(
            ClaroTypeException.forUnexpectedIdentifierRedeclaration(endpoint.getKey().identifier));
      }
      if (endpoint.getValue() instanceof FormatStringExpr) {
        for (Expr fmtStringExpr : ((FormatStringExpr) endpoint.getValue()).fmtExprArgs) {
          if (!(fmtStringExpr instanceof IdentifierReferenceTerm)) {
            fmtStringExpr.logTypeError(ClaroTypeException.forInvalidHttpEndpointPathVariable());
          }
        }
      }
    }

    // We'll need to register these types ahead of time so that any `endpoint_handler` blocks can be validated against
    // the set of procedure defs that must be implemented.
    ImmutableMap.Builder<String, Types.ProcedureType> endpointHandlerProcedureTypes = ImmutableMap.builder();

    // Simply put a function type signature in the symbol table for each endpoint.
    for (Map.Entry<IdentifierReferenceTerm, Object> endpoint : this.endpoints.entrySet()) {
      ImmutableMap.Builder<String, TypeProvider> endpointFuncArgsBuilder = ImmutableMap.<String, TypeProvider>builder()
          .put("$httpClient", (scopedHeap1) -> Types.HttpClientType.forServiceName(this.serviceName.identifier));
      ProcedureDefinitionStmt endpointProcDefStmt;
      if (endpoint.getValue() instanceof FormatStringExpr) {
        ((FormatStringExpr) endpoint.getValue()).fmtExprArgs.forEach(
            pathArg ->
                endpointFuncArgsBuilder.put(
                    ((IdentifierReferenceTerm) pathArg).identifier, TypeProvider.ImmediateTypeProvider.of(Types.STRING)));
        endpointHandlerProcedureTypes.put(
            endpoint.getKey().identifier,
            Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
                ((FormatStringExpr) endpoint.getValue()).fmtExprArgs.stream()
                    .map(unused -> Types.STRING)
                    .collect(ImmutableList.toImmutableList()),
                Types.FutureType.wrapping(Types.HTTP_RESPONSE),
                /*explicitlyAnnotatedBlocking=*/false
            )
        );
      } else {
        endpointHandlerProcedureTypes.put(
            endpoint.getKey().identifier,
            Types.ProcedureType.ProviderType.typeLiteralForReturnType(
                Types.FutureType.wrapping(Types.HTTP_RESPONSE),
                /*explicitlyAnnotatedBlocking=*/false
            )
        );
      }
      endpointProcDefStmt = new FunctionDefinitionStmt(
          endpoint.getKey().identifier,
          BaseType.FUNCTION,
          endpointFuncArgsBuilder.build(),
          TypeProvider.ImmediateTypeProvider.of(
              Types.FutureType.wrapping(
                  Types.OneofType.forVariantTypes(
                      ImmutableList.of(
                          Types.STRING,
                          Types.UserDefinedType.forTypeNameAndParameterizedTypes(
                              "Error", ImmutableList.of(Types.STRING))
                      )))),
          syntheticHttpProcStmtList.apply(
              endpoint.getValue() instanceof FormatStringExpr
              ? ((FormatStringExpr) endpoint.getValue()).fmtExprArgs
              : ImmutableList.of(),
              endpoint.getKey().identifier
          )
      );
      endpointProcDefStmt.registerProcedureTypeProvider(scopedHeap);
      this.syntheticEndpointProcedures.add(endpointProcDefStmt);
    }
    endpointHandlerProcedureTypes.build().forEach(
        (endpointName, endpointHandlerSignature) ->
            InternalStaticStateUtil.HttpServiceDef_endpointProcedureSignatures.put(
                this.serviceName.identifier, endpointName, endpointHandlerSignature));
  }

  public void registerTypeProvider(ScopedHeap scopedHeap) {
    if (scopedHeap.isIdentifierDeclared(this.serviceName.identifier)) {
      this.serviceName.logTypeError(ClaroTypeException.forUnexpectedIdentifierRedeclaration(this.serviceName.identifier));
    }

    // Now place this in the symbol table so that nothing else can shadow this name.
    scopedHeap.putIdentifierValue(this.serviceName.identifier, Types.HttpServiceType.forServiceName(this.serviceName.identifier));
    scopedHeap.markIdentifierAsTypeDefinition(this.serviceName.identifier);
    scopedHeap.markIdentifierUsed(this.serviceName.identifier);
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Assert types on the synthetic endpoint procedures just for the sake of readying them for codegen.
    for (ProcedureDefinitionStmt p : this.syntheticEndpointProcedures) {
      p.assertExpectedExprTypes(scopedHeap);
    }

    // Now that everything's validated, just make note of the endpoints' paths so that any generated server can actually
    // register the corresponding endpoint handlers.
    this.endpoints.forEach(
        (key, value) -> {
          StringBuilder formattedPathStr = null;
          if (value instanceof FormatStringExpr) {
            formattedPathStr = new StringBuilder();
            FormatStringExpr fmt = (FormatStringExpr) value;
            int i;
            for (i = 0; i < fmt.fmtExprArgs.size(); i++) {
              formattedPathStr
                  .append(fmt.fmtStringParts.get(i))
                  .append(':')
                  .append(((IdentifierReferenceTerm) fmt.fmtExprArgs.get(i)).identifier);
            }
            if (i < fmt.fmtStringParts.size()) {
              formattedPathStr.append(fmt.fmtStringParts.get(i));
            }
          }
          InternalStaticStateUtil.HttpServiceDef_endpointPaths.put(
              this.serviceName.identifier,
              key.identifier,
              formattedPathStr == null ? (String) value : formattedPathStr.toString()
          );
        });
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = GeneratedJavaSource.forStaticDefinitions(new StringBuilder());
    res.optionalStaticDefinitions().get()
        .append("public interface ")
        .append(this.serviceName.identifier)
        .append(" {\n");
    GeneratedJavaSource finalRes = res;
    this.endpoints.entrySet().forEach(
        e -> {
          finalRes.optionalStaticDefinitions().get()
              .append("\t@GET(\"");
          if (e.getValue() instanceof FormatStringExpr) {
            FormatStringExpr fmt = (FormatStringExpr) e.getValue();
            Streams.forEachPair(
                fmt.fmtStringParts.stream(),
                fmt.fmtExprArgs.stream(),
                (fmtStringPart, fmtArgPart) ->
                    finalRes.optionalStaticDefinitions().get()
                        .append(fmtStringPart)
                        .append("{")
                        .append(((IdentifierReferenceTerm) fmtArgPart).identifier)
                        .append("}")
            );
            if (fmt.fmtStringParts.size() > fmt.fmtExprArgs.size()) {
              finalRes.optionalStaticDefinitions().get().append(fmt.fmtStringParts.get(fmt.fmtStringParts.size() - 1));
            }
          } else {
            finalRes.optionalStaticDefinitions().get().append(e);
          }
          finalRes.optionalStaticDefinitions().get()
              .append("\")\n\tretrofit2.Call<ResponseBody> ")
              .append(e.getKey().identifier)
              .append("(");
          if (e.getValue() instanceof FormatStringExpr) {
            ImmutableList<Expr> fmtExprArgs = ((FormatStringExpr) e.getValue()).fmtExprArgs;
            fmtExprArgs.subList(0, fmtExprArgs.size() - 1).forEach(
                pathArg -> finalRes.optionalStaticDefinitions().get()
                    .append("@Path(\"")
                    .append(((IdentifierReferenceTerm) pathArg).identifier)
                    .append("\") ")
                    .append("String ")
                    .append(((IdentifierReferenceTerm) pathArg).identifier)
                    .append(", ")
            );
            finalRes.optionalStaticDefinitions().get()
                .append("@Path(\"")
                .append(((IdentifierReferenceTerm) fmtExprArgs.get(fmtExprArgs.size() - 1)).identifier)
                .append("\") ")
                .append("String ")
                .append(((IdentifierReferenceTerm) fmtExprArgs.get(fmtExprArgs.size() - 1)).identifier);
          }
          finalRes.optionalStaticDefinitions().get().append(");\n");
        }
    );
    res.optionalStaticDefinitions().get()
        .append("}\n");

    // Finally, codegen each of the synthetic endpoint procedures.
    for (ProcedureDefinitionStmt p : this.syntheticEndpointProcedures) {
      res = res.createMerged(p.generateJavaSourceOutput(scopedHeap));
    }

    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Eventually need to impl httpservice when I come back to adding support for the interpreted backend.
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support `HttpService` in the interpreted backend just yet!");
  }
}
