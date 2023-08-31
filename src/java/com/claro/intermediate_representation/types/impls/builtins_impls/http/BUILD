package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/claro_programs:__subpackages__",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization:__subpackages__",
        "//src/java/com/claro/intermediate_representation/expressions:__pkg__",
        "//src/java/com/claro/intermediate_representation/statements:__pkg__",
        "//src/java/com/claro/runtime_utilities:__subpackages__",
        "//src/java/com/claro/stdlib:__subpackages__",
    ]
)

java_library(
    name = "http_response",
    srcs = ["$ClaroHttpResponse.java"],
    deps = [
        "//:activej_http",
        "//:guava",
        "//:autovalue",
        "//src/java/com/claro/intermediate_representation/types",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/procedures",
    ],
)
