package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/intermediate_representation/expressions:__subpackages__",
        "//src/java/com/claro/intermediate_representation/statements:__subpackages__",
        "//src/java/com/claro/intermediate_representation:__pkg__",
    ]
)


java_library(
    name = "bool_expr_impls",
    srcs = glob(
        ["*BoolExpr.java"],
        exclude=["BoolExpr.java"]
    ),
    deps = [
        ":bool_expr",
        "//:guava",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:types",
    ],
)

java_library(
    name = "bool_expr",
    srcs = ["BoolExpr.java"],
    deps = [
        "//:guava",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:types",
    ],
)