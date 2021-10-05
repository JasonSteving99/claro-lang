package(
    default_visibility = [
        "//src/java/com/claro/examples/calculator_example:__pkg__",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation:__subpackages__",
    ]
)

java_library(
    name = "stmt_list_node",
    srcs = ["StmtListNode.java"],
    deps = [
        ":stmt",
        "//:guava",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation:node",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types:claro_type_exception",
    ],
)

java_library(
    name = "stmt_impls",
    srcs = glob(["*Stmt.java"], exclude = ["Stmt.java"]),
    deps = [
        ":stmt",
        ":stmt_list_node",
        "//:apache_commons_text",
        "//:guava",
        "//src/java/com/claro/examples/calculator_example:calculator_parser_exception",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation:node",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/expressions:expr",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/expressions:expr_impls",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/expressions/bool:bool_expr",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/expressions/term:term_impls",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types:base_type",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types:type",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types:types",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types/builtins_impls/collections:collections_impls",
    ],
)

java_library(
    name = "stmt",
    srcs = ["Stmt.java"],
    deps = [
        "//:guava",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation:node",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:scoped_heap",
    ],
)