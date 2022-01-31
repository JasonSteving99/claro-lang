package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/intermediate_representation:__subpackages__",
        "//src/java/com/claro/intermediate_representation/expressions:__pkg__",
    ]
)

java_library(
    name = "stmt_list_node",
    srcs = [
        # We're bringing in all of these classes other than StmtListNode here because we need a Java-supported
        # circular dependency from StmtListNode <-> ProcedureDefinitionStmt, but Bazel can't handle that since
        # it has no way of knowing that it's valid.
        "DeclarationStmt.java",
        "ProcedureDefinitionStmt.java",
        "ReturnStmt.java",
        "StmtListNode.java",
    ],
    deps = [
        ":stmt",
        "//:guava",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
        "//src/java/com/claro/intermediate_representation/types:types",
    ],
)

java_library(
    name = "stmt_impls",
    srcs = glob(
        ["*Stmt.java"],
        exclude = [
            "Stmt.java",
            # We need a circular dependency that Java can resolve in StmtListNode, but Bazel can't handle it.
            "DeclarationStmt.java",
            "ProcedureDefinitionStmt.java",
            "ReturnStmt.java",
        ]
    ),
    deps = [
        ":stmt",
        ":stmt_list_node",
        "//:apache_commons_text",
        "//:guava",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/expressions:expr_impls",
        "//src/java/com/claro/intermediate_representation/expressions/bool:bool_expr",
        "//src/java/com/claro/intermediate_representation/expressions/term:term_impls",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/collections:collections_impls",
    ],
)

java_library(
    name = "stmt",
    srcs = ["Stmt.java"],
    deps = [
        "//:guava",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
    ],
)