package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/intermediate_representation/expressions:__subpackages__",
        "//src/java/com/claro/intermediate_representation/statements:__subpackages__",
        "//src/java/com/claro/intermediate_representation/types:__pkg__",
        "//src/java/com/claro/intermediate_representation:__pkg__",
    ]
)

java_library(
    name = "expr_impls",
    srcs = glob(
        ["*Expr.java"],
        exclude = [
            "Expr.java",
            "*NumericExpr.java",
        ]),
    deps = [
        "//:guava",
        "//src/java/com/claro/runtime_utilities:runtime_utilities",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/expressions/bool:bool_expr_impls",
        "//src/java/com/claro/intermediate_representation/expressions/numeric:numeric_expr_impls",
        "//src/java/com/claro/intermediate_representation/expressions/procedures:procedures",
        "//src/java/com/claro/intermediate_representation/expressions/term:term_impls",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls:builtins_impls",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/collections:collection_interface",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/collections:collections_impls",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:types",
    ],
)

java_library(
    name = "expr",
    srcs = ["Expr.java"],
    deps = [
        "//:guava",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:concrete_types",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
    ],
)