package com.claro.stdlib;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.stdlib.http.$ClaroHttpResponseStdLibRegistryUtil;
import com.claro.stdlib.http.$HttpServerStdLibRegistryUtil;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;

import java.util.function.BiFunction;

// TODO(steving) This entire stdlib registry was always a massive hack and has only worsened. Obviously these procedures
//   instead need to be migrated to a proper stdlib module implementation once generic procedure exports are supported.
public class StdLibRegistry {
  public static final ImmutableTable<String, Type, Object> stdLibProcedureTypes =
      ImmutableTable.<String, Type, Object>builder()
          .put(
              $HttpServerStdLibRegistryUtil.getProcedureName(),
              $HttpServerStdLibRegistryUtil.getProcedureType(),
              // Generic function defs actually don't put a procedure wrapper in the symbol table, they put a function
              // that gives you the monomorphized procedure name. Here we won't actually do monomorphization, so this is
              // just a formality.
              (BiFunction<ScopedHeap, ImmutableMap<Type, Type>, String>) (scopedHeap, unused) -> "com.claro.runtime_utilities.http.$ClaroHttpServer.startServerAndAwaitShutdown"
          )
          .put(
              "com.claro.intermediate_representation.types.impls.builtins_impls.http.$ClaroHttpResponse.getOk200HttpResponseForHtml",
              $ClaroHttpResponseStdLibRegistryUtil.getOk200HttpResponseForHtmlProcedureType(),
              $ClaroHttpResponseStdLibRegistryUtil.getOk200HttpResponseForHtmlProcedureWrapper
          )
          .put(
              "com.claro.intermediate_representation.types.impls.builtins_impls.http.$ClaroHttpResponse.getOk200HttpResponseForJson",
              $ClaroHttpResponseStdLibRegistryUtil.getOk200HttpResponseForJsonProcedureType(),
              $ClaroHttpResponseStdLibRegistryUtil.getOk200HttpResponseForJsonProcedureWrapper
          )
          .build();

  public static final ImmutableMap<String, Type> stdLibTypeDefs =
      ImmutableMap.<String, Type>builder()
          .put("HttpResponse", Types.HTTP_RESPONSE)
          .build();
}
