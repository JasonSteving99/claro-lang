package(default_visibility = ["//src/java/com/claro/repl_site:__subpackages__"])


java_binary(
    name = "claro_javalin_server_binary",
    main_class = "com.claro.repl_site.server.ClaroReplServer",
    runtime_deps = [
        ":claro_javalin_server_library",
        "//:slf4j",
    ]
)

java_library(
    name = "claro_javalin_server_library",
    srcs = ["ClaroReplServer.java"],
    deps = [
        "//:javalin",
        "//src/java/com/claro/repl_site/server/handlers",
    ],
)
