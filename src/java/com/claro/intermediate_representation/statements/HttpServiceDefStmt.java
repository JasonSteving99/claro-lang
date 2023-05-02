package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.FormatStringExpr;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

public class HttpServiceDefStmt extends Stmt {
  private final IdentifierReferenceTerm serviceName;
  private final ImmutableMap<IdentifierReferenceTerm, FormatStringExpr> endpoints;
  private ArrayList<ProcedureDefinitionStmt> syntheticEndpointProcedures = new ArrayList<>();

  public HttpServiceDefStmt(IdentifierReferenceTerm serviceName, ImmutableMap<IdentifierReferenceTerm, FormatStringExpr> endpoints) {
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
                        argNames.subList(0, argNames.size() - 1).forEach(
                            arg ->
                                res.javaSourceBody().append(((IdentifierReferenceTerm) arg).identifier).append(", "));
                        res.javaSourceBody()
                            .append(((IdentifierReferenceTerm) argNames.get(argNames.size() - 1)).identifier)
                            .append("))");
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

    // TODO(steving) Long term I most likely don't want these to end up being top-level identifiers, but for now it'll
    //   be the best I can do until I have proper namespacing support.
    for (Map.Entry<IdentifierReferenceTerm, FormatStringExpr> endpoint : this.endpoints.entrySet()) {
      if (scopedHeap.isIdentifierDeclared(endpoint.getKey().identifier)) {
        endpoint.getKey().logTypeError(
            ClaroTypeException.forUnexpectedIdentifierRedeclaration(endpoint.getKey().identifier));
      }
      for (Expr fmtStringExpr : endpoint.getValue().fmtExprArgs) {
        if (!(fmtStringExpr instanceof IdentifierReferenceTerm)) {
          fmtStringExpr.logTypeError(ClaroTypeException.forInvalidHttpEndpointPathVariable());
        }
      }
    }

    // Simply put a function type signature in the symbol table for each endpoint.
    for (Map.Entry<IdentifierReferenceTerm, FormatStringExpr> endpoint : this.endpoints.entrySet()) {
      ImmutableMap.Builder<String, TypeProvider> endpointFuncArgsBuilder = ImmutableMap.<String, TypeProvider>builder()
          .put("$httpClient", (scopedHeap1) -> Types.HttpClientType.forServiceName(this.serviceName.identifier));
      endpoint.getValue().fmtExprArgs.forEach(
          pathArg ->
              endpointFuncArgsBuilder.put(
                  ((IdentifierReferenceTerm) pathArg).identifier, TypeProvider.ImmediateTypeProvider.of(Types.STRING)));
      FunctionDefinitionStmt endpointFuncDefStmt = new FunctionDefinitionStmt(
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
          syntheticHttpProcStmtList.apply(endpoint.getValue().fmtExprArgs, endpoint.getKey().identifier)
      );
      endpointFuncDefStmt.registerProcedureTypeProvider(scopedHeap);
      this.syntheticEndpointProcedures.add(endpointFuncDefStmt);
    }
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
          Streams.forEachPair(
              e.getValue().fmtStringParts.stream(),
              e.getValue().fmtExprArgs.stream(),
              (fmtStringPart, fmtArgPart) ->
                  finalRes.optionalStaticDefinitions().get()
                      .append(fmtStringPart)
                      .append("{")
                      .append(((IdentifierReferenceTerm) fmtArgPart).identifier)
                      .append("}")
          );
          finalRes.optionalStaticDefinitions().get()
              .append("\")\n\tretrofit2.Call<ResponseBody> ")
              .append(e.getKey().identifier)
              .append("(");
          e.getValue().fmtExprArgs.subList(0, e.getValue().fmtExprArgs.size() - 1).forEach(
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
              .append(((IdentifierReferenceTerm)
                           e.getValue().fmtExprArgs.get(e.getValue().fmtExprArgs.size() - 1)).identifier)
              .append("\") ")
              .append("String ")
              .append(((IdentifierReferenceTerm)
                           e.getValue().fmtExprArgs.get(e.getValue().fmtExprArgs.size() - 1)).identifier)
              .append(");\n");
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
