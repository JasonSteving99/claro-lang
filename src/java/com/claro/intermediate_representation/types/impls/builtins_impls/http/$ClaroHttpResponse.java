package com.claro.intermediate_representation.types.impls.builtins_impls.http;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.builtins_impls.ClaroBuiltinTypeImplementation;
import com.claro.intermediate_representation.types.impls.builtins_impls.procedures.ClaroFunction;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import io.activej.http.HttpResponse;

@AutoValue
public abstract class $ClaroHttpResponse implements ClaroBuiltinTypeImplementation {
  public abstract HttpResponse getHttpResponse();

  public static ClaroFunction<$ClaroHttpResponse> getOk200HttpResponseForHtml =
      new ClaroFunction<$ClaroHttpResponse>() {
        @Override
        public $ClaroHttpResponse apply(Object... args) {
          return getOk200HttpResponseForHtmlImpl((String) args[0]);
        }

        @Override
        public Type getClaroType() {
          return Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
              ImmutableList.of(Types.STRING), Types.HTTP_RESPONSE, /*explicitlyAnnotatedBlocking=*/false);
        }
      };

  public static ClaroFunction<$ClaroHttpResponse> getOk200HttpResponseForJson =
      new ClaroFunction<$ClaroHttpResponse>() {
        @Override
        public $ClaroHttpResponse apply(Object... args) {
          return getOk200HttpResponseForJsonImpl((String) args[0]);
        }

        @Override
        public Type getClaroType() {
          return Types.ProcedureType.FunctionType.typeLiteralForArgsAndReturnTypes(
              ImmutableList.of(Types.STRING), Types.HTTP_RESPONSE, /*explicitlyAnnotatedBlocking=*/false);
        }
      };

  public static $ClaroHttpResponse getOk200HttpResponseForHtmlImpl(String html) {
    return new AutoValue_$ClaroHttpResponse(HttpResponse.ok200().withHtml(html));
  }

  public static $ClaroHttpResponse getOk200HttpResponseForJsonImpl(String json) {
    return new AutoValue_$ClaroHttpResponse(HttpResponse.ok200().withJson(json));
  }

  @Override
  public Type getClaroType() {
    return Types.HTTP_RESPONSE;
  }

  @Override
  public String toString() {
    return "HttpResponse";
  }
}
