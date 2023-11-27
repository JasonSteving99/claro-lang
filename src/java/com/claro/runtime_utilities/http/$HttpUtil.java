package com.claro.runtime_utilities.http;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.builtins_impls.futures.ClaroFuture;
import com.claro.intermediate_representation.types.impls.user_defined_impls.$UserDefinedType;
import com.claro.stdlib.StdLibModuleRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.SettableFuture;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class $HttpUtil {

  private static OkHttpClient OKHTTP_CLIENT = null;
  private static final Map<String, Retrofit> CACHED_RETROFIT_CLIENTS_BY_BASE_URL = Maps.newConcurrentMap();

  private static OkHttpClient getOkHttpClient() {
    if ($HttpUtil.OKHTTP_CLIENT == null) {
      $HttpUtil.OKHTTP_CLIENT = new OkHttpClient.Builder()
          // TODO(steving) I want to update this to actually allow a user configured timeout and default to 10 OkHttp's
          //  defualt 10sec read timeout. But for now, hardcoding 0 for NO timeout whatsoever.
          .readTimeout(0, TimeUnit.SECONDS)
          .build();
    }
    return $HttpUtil.OKHTTP_CLIENT;
  }

  public static void shutdownOkHttpClient() {
    if ($HttpUtil.OKHTTP_CLIENT != null) {
      $HttpUtil.OKHTTP_CLIENT.dispatcher().executorService().shutdown();
    }
  }

  public static <T> T getServiceClientForBaseUrl(Class<T> generatedServiceClass, String baseUrl) {
    return CACHED_RETROFIT_CLIENTS_BY_BASE_URL.computeIfAbsent(
            baseUrl,
            _baseUrl ->
                new Retrofit.Builder()
                    .baseUrl(_baseUrl)
                    .client(getOkHttpClient())
                    .build()
        )
        .create(generatedServiceClass);
  }

  // TODO(steving) Long term this should really be updated to return the Response itself rather than just the body. It
  //  should just be up to the user how they want to handle the response.
  public static ClaroFuture<Object> executeAsyncHttpRequest(Call<ResponseBody> callAsync) {
    SettableFuture<Object> settableFuture = SettableFuture.create();

    callAsync.enqueue(new Callback<ResponseBody>() {
      @Override
      public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
        if (!response.isSuccessful()) {
          settableFuture.set(getSimpleErrorType(Types.STRING, "HTTP GET FAILURE!: " + response));
          return;
        }

        try (ResponseBody responseBody = response.body()) {
          settableFuture.set(responseBody.string());
        } catch (IOException e) {
          settableFuture.setException(e);
        }
      }

      @Override
      public void onFailure(Call<ResponseBody> call, Throwable throwable) {
        settableFuture.set(getSimpleErrorType(
            Types.STRING,
            "HTTP GET FAILURE!: " + throwable.getMessage() + "\n" + Arrays.toString(throwable.getStackTrace())
        ));
      }
    });

    return new ClaroFuture<>(
        Types.OneofType.forVariantTypes(
            ImmutableList.of(
                Types.STRING,
                Types.UserDefinedType.forTypeNameAndParameterizedTypes(
                    "Error",
                    StdLibModuleRegistry.STDLIB_MODULE_DISAMBIGUATOR,
                    ImmutableList.of(Types.STRING)
                )
            )),
        settableFuture
    );
  }

  private static <T> $UserDefinedType<T> getSimpleErrorType(Type wrappedType, T wrappedValue) {
    return new $UserDefinedType<>("Error", StdLibModuleRegistry.STDLIB_MODULE_DISAMBIGUATOR, ImmutableList.of(wrappedType), wrappedType, wrappedValue);
  }
}
