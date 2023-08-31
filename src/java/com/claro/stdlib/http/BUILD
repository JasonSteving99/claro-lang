package(
    default_visibility = [
        "//src/java/com/claro/claro_programs:__subpackages__",
        "//src/java/com/claro/stdlib:__subpackages__",
    ]
)

java_library(
    name = "http",
    srcs = glob(["*.java"]),
    deps = [
        "//:guava",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/statements:stmt",
        "//src/java/com/claro/intermediate_representation/statements/user_defined_type_def_stmts",
        "//src/java/com/claro/intermediate_representation/types",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/futures:ClaroFuture",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/http:http_response",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/procedures",
    ],
)
