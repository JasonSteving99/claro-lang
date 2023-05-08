package com.claro.runtime_utilities.http;

import com.claro.runtime_utilities.ClaroRuntimeUtilities;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.activej.eventloop.Eventloop;
import io.activej.http.*;
import io.activej.promise.SettablePromise;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

public class $ClaroHttpServer extends $ClaroLauncher {
  // TODO(steving) TESTING!! Unnecessary
  private static final ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newScheduledThreadPool(50);

  public $ClaroHttpServer(AsyncServlet routingServlet, InetSocketAddress serverAddress) {
    super(
        Eventloop.create(),
        eventLoop -> AsyncHttpServer.create(eventLoop, routingServlet)
            .withListenAddresses(ImmutableList.of(serverAddress))
    );
  }

  @Override
  protected void run() throws Exception {
    System.out.println("HTTP Server is now available at " + String.join(", ", super.server.getHttpAddresses()));
    awaitShutdown();
  }

  // TODO(steving) TESTING!!! This is all totally just testing. These methods will of course instead be replaced
  //   by calls to actual user logic.
  private static ListenableFuture<HttpResponse> getRootEndpointResponse(
      ScheduledExecutorService scheduledExecutorService, HttpRequest unused) {
    return Futures.scheduleAsync(
        () -> Futures.immediateFuture(
            HttpResponse.ok200()
                .withHtml(
                    "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "  <title>List of Links</title>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "  <h1>Site Directory</h1>\n" +
                    "  <ul>\n" +
                    "    <li><a href=\"/page1\">Page 1</a></li>\n" +
                    "    <li><a href=\"/page2\">Page 2</a></li>\n" +
                    "    <li><a href=\"/page3\">Page 3 (will intentionally cause internal server error)</a></li>\n" +
                    "    <li><a href=\"/page4\">Page 4 (will return JSON)</a></li>\n" +
                    "  </ul>\n" +
                    "</body>\n" +
                    "</html>\n"
                )
        ),
        Duration.ofSeconds(1),
        scheduledExecutorService
    );
  }

  // TODO(steving) TESTING!!! This is all totally just testing. These methods will of course instead be replaced
  //   by calls to actual user logic.
  private static ListenableFuture<HttpResponse> getPageResponse(int pageNum, HttpRequest unused) {
    if (pageNum == 3) {
      // Intentionally throw runtime exception to see what happens.
      return Futures.immediateFailedFuture(new RuntimeException("TESTING!!! CAUSING RUNTIME EXCEPTION!"));
    }
    return Futures.immediateFuture(
        HttpResponse.ok200()
            .withHtml(
                "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <title>List of Links</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <h1>You made it to page " + pageNum + "!</h1>\n" +
                "  <ul>\n" +
                "    <li><a href=\"/\">Go Back to Home</a></li>\n" +
                "  </ul>\n" +
                "</body>\n" +
                "</html>\n"
            )
    );
  }

  // TODO(steving) TESTING!!! This is all totally just testing. These methods will of course instead be replaced
  //   by calls to actual user logic.
  private static ListenableFuture<HttpResponse> getJSONResponse(HttpRequest unused) {
    return Futures.immediateFuture(
        HttpResponse.ok200().withJson(
            "{" +
            "  \"Field 1\": \"This is arbitrary Json.\"," +
            "  \"Field 2\": 99" +
            "}"
        )
    );
  }

  private static AsyncServlet getBasicAsyncServlet(
      String endpoint, Function<HttpRequest, ListenableFuture<HttpResponse>> endpointHandler) {
    return request -> {
      // TODO(steving) TESTING!!! This is all totally just testing. Drop the prints.
      System.out.println("GOT REQUEST! AT ENDPOINT: " + endpoint);
      SettablePromise<HttpResponse> promise = new SettablePromise<>();
      Futures.addCallback(
          endpointHandler.apply(request),
          new $ClaroHttpEndpointResultHandler(promise),
          ClaroRuntimeUtilities.DEFAULT_EXECUTOR_SERVICE
      );
      return promise;
    };
  }

  // TODO(steving) TESTING!! DROP THE MAIN METHOD
  public static void main(String[] args) throws Exception {
    $ClaroHttpServer launcher = new $ClaroHttpServer(
        RoutingServlet.create()
            .map(
                HttpMethod.GET,
                "/",
                getBasicAsyncServlet("/", httpRequest -> getRootEndpointResponse(SCHEDULED_EXECUTOR, httpRequest))
            )
            .map(
                HttpMethod.GET,
                "/page1",
                getBasicAsyncServlet("/page1", httpRequest -> getPageResponse(1, httpRequest))
            )
            .map(
                HttpMethod.GET,
                "/page2",
                getBasicAsyncServlet("/page2", httpRequest -> getPageResponse(2, httpRequest))
            )
            .map(
                HttpMethod.GET,
                "/page3",
                getBasicAsyncServlet("/page3", httpRequest -> getPageResponse(3, httpRequest))
            )
            .map(
                HttpMethod.GET,
                "/page4",
                getBasicAsyncServlet("/page4", httpRequest -> getJSONResponse(httpRequest))
            ),
        new InetSocketAddress(8080)
    );
    launcher.launch();
  }
}

class $ClaroHttpEndpointResultHandler implements FutureCallback<HttpResponse> {
  private final SettablePromise<HttpResponse> promise;

  $ClaroHttpEndpointResultHandler(SettablePromise<HttpResponse> promise) {
    this.promise = promise;
  }

  @Override
  public void onSuccess(HttpResponse httpResponse) {
    promise.set(httpResponse);
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
