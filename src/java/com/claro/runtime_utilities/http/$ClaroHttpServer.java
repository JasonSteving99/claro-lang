package com.claro.runtime_utilities.http;

import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.intermediate_representation.types.impls.builtins_impls.futures.ClaroFuture;
import com.claro.intermediate_representation.types.impls.builtins_impls.http.$ClaroHttpResponse;
import com.claro.intermediate_representation.types.impls.builtins_impls.procedures.ClaroConsumerFunction;
import com.claro.runtime_utilities.ClaroRuntimeUtilities;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.activej.eventloop.Eventloop;
import io.activej.http.*;
import io.activej.promise.SettablePromise;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Optional;
import java.util.function.Function;

public class $ClaroHttpServer extends $ClaroLauncher {

  public static final HttpMethod GET = HttpMethod.GET;
  public static boolean silent = false;

  public static ClaroConsumerFunction<$ClaroHttpServer> startServerAndAwaitShutdown =
      new ClaroConsumerFunction<$ClaroHttpServer>() {
        @Override
        public void apply(Object... args) {
          try {
            startServerAndAwaitShutdownImpl(($ClaroHttpServer) args[0]);
          } catch (Exception e) {
            throw new ClaroFuture.Panic(e);
          }
        }

        @Override
        public Type getClaroType() {
          return Types.ProcedureType.ConsumerType.typeLiteralForConsumerArgTypes(
              ImmutableList.of(Types.HttpServerType.forHttpService(Types.$GenericTypeParam.forTypeParamName("T"))),
              /*explicitlyAnnotatedBlocking=*/false,
              /*optionalAnnotatedBlockingGenericOverArgs=*/Optional.empty(),
              Optional.of(ImmutableList.of("T"))
          );
        }
      };

  public static void startServerAndAwaitShutdownImpl($ClaroHttpServer server) throws Exception {
    server.launch();
  }

  public $ClaroHttpServer(AsyncServlet routingServlet, InetSocketAddress serverAddress) {
    super(
        Eventloop.create(),
        eventLoop -> AsyncHttpServer.create(eventLoop, routingServlet)
            .withListenAddresses(ImmutableList.of(serverAddress))
    );
  }

  @Override
  protected void run() throws Exception {
    if (!$ClaroHttpServer.silent) {
      System.out.println("HTTP Server is now available at " + String.join(", ", super.server.getHttpAddresses()));
    }
    awaitShutdown();
  }

  public static AsyncServlet getBasicAsyncServlet(
      String endpoint, Function<HttpRequest, ListenableFuture<? extends $ClaroHttpResponse>> endpointHandler) {
    return request -> {
      SettablePromise<HttpResponse> promise = new SettablePromise<>();
      Futures.addCallback(
          endpointHandler.apply(request),
          new $ClaroHttpEndpointResultHandler(promise),
          ClaroRuntimeUtilities.DEFAULT_EXECUTOR_SERVICE
      );
      return promise;
    };
  }

  public static RoutingServlet getRoutingServlet() {
    return RoutingServlet.create();
  }

  public static InetSocketAddress getInetSocketAddressForPort(int port) {
    // This more complex approach to instantiating an InetSocketAddress first goes through ServerSocket so that if the
    // user chooses port 0 then ServerSocket will actually find and claim any available port automatically.
    try (ServerSocket ss = new ServerSocket(port)) {
      return new InetSocketAddress(ss.getInetAddress(), ss.getLocalPort());
    } catch (Exception e) {
      throw new ClaroFuture.Panic(e);
    }
  }
}

class $ClaroHttpEndpointResultHandler implements FutureCallback<$ClaroHttpResponse> {
  private final SettablePromise<HttpResponse> promise;

  $ClaroHttpEndpointResultHandler(SettablePromise<HttpResponse> promise) {
    this.promise = promise;
  }

  @Override
  public void onSuccess($ClaroHttpResponse claroHttpResponse) {
    promise.set(claroHttpResponse.getHttpResponse());
  }

  @Override
  public void onFailure(Throwable throwable) {
    // TODO(steving) Long term, I must determine some more resilient scheme for handling errors.
    promise.set(
        HttpResponse.ofCode(500)
            .withPlainText(
                "Unhandled Runtime Exception in Http Endpoint Handler!\n" + throwable));
  }
}
