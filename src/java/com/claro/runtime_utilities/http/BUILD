package(
    default_visibility = [
        "//src/java/com/claro/claro_programs:__subpackages__",
        "//src/java/com/claro/stdlib:__subpackages__",
    ]
)

java_library(
    name = "http",
    srcs = ["$HttpUtil.java"],
    deps = [
        "//:guava",
        "//:okhttp",
        "//:retrofit",
        "//src/java/com/claro/intermediate_representation/types",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/futures:ClaroFuture",
        "//src/java/com/claro/intermediate_representation/types/impls/user_defined_impls:user_defined_impls",
        "//src/java/com/claro/runtime_utilities",
    ],
)

java_library(
    name = "http_server",
    srcs = ["$ClaroHttpServer.java", "$ClaroLauncher.java"],
    deps = [
        "//:activej_common",
        "//:activej_eventloop",
        "//:activej_http",
        "//:activej_promise",

        "//:guava",
        "//src/java/com/claro/intermediate_representation/types",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/futures:ClaroFuture",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/http:http_response",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/procedures",
        "//src/java/com/claro/runtime_utilities",
    ],
)