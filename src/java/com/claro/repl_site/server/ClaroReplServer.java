package com.claro.repl_site.server;

import com.claro.repl_site.server.handlers.RootArgHandler;
import io.javalin.Javalin;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class ClaroReplServer {

  public static void main(String[] args) {
    Javalin app = Javalin.create();
    app.routes(
        () -> {
          path(
              "/",
              () -> {
                path(
                    "{arg}",
                    () -> {
                      get(new RootArgHandler());
                    }
                );
              }
          );
        }
    ).start(7000);
  }
}
