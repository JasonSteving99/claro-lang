package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/intermediate_representation/expressions:__subpackages__",
        "//src/java/com/claro/intermediate_representation:__pkg__",
    ]
)


java_library(
    name = "methods",
    srcs = glob(["*MethodCallExpr.java"]),
    deps = [
        "//:guava",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/intermediate_representation/types/impls/user_defined_impls",
        "//src/java/com/claro/intermediate_representation/types/impls/user_defined_impls/structs",
    ],
)