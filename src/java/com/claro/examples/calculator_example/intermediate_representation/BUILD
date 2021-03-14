package(
    default_visibility = [
        "//src/java/com/claro/examples/calculator_example:__pkg__",
        "//src/java/com/claro/examples/calculator_example/compiler_backends:__subpackages__",
    ]
)

java_library(
    name = "expr_impls",
    srcs = [
        "InputExpr.java",
        "ListExpr.java",
        "ListSubscriptExpr.java",
        "ParenthesizedExpr.java",
    ],
    deps = [
        ":bool_expr_impls",
        ":expr",
        ":node",
        ":numeric_expr_impls",
        "//:guava",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:scoped_heap",
    ],
)

java_library(
    name = "numeric_expr_impls",
    srcs = glob(
        ["*NumericExpr.java"],
        exclude=["NumericExpr.java"]
    ),
    deps = [
        ":numeric_expr",
        ":expr",
        "//:guava",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:scoped_heap",
    ],
)

java_library(
    name = "numeric_expr",
    srcs = ["NumericExpr.java"],
    deps = [
        ":expr",
        ":node",
        "//:guava",
    ],
)

java_library(
    name = "bool_expr_impls",
    srcs = glob(
        ["*BoolExpr.java"],
        exclude=["BoolExpr.java"]
    ),
    deps = [
        ":bool_expr",
        ":expr",
        "//:guava",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:scoped_heap"
    ],
)

java_library(
    name = "bool_expr",
    srcs = ["BoolExpr.java"],
    deps = [
        ":expr",
        ":node",
        "//:guava",
    ],
)

java_library(
    name = "term_impls",
    srcs = glob(
        ["*Term.java"],
        exclude = ["Term.java"],
    ),
    deps = [
        ":term",
        "//src/java/com/claro/examples/calculator_example:calculator_parser_exception",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:scoped_heap",
    ],
)

java_library(
    name = "term",
    srcs = ["Term.java"],
    deps = [
        ":expr",
        "//:guava",
    ],
)

java_library(
    name = "expr",
    srcs = ["Expr.java"],
    deps = [
        ":node",
        "//:guava",
    ],
)

java_library(
    name = "stmt_list_node",
    srcs = ["StmtListNode.java"],
    deps = [
        ":node",
        ":stmt",
        "//:guava",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:scoped_heap"
    ],
)

java_library(
    name = "stmt_impls",
    srcs = glob(["*Stmt.java"], exclude = ["Stmt.java"]),
    deps = [
        ":bool_expr",
        ":expr",
        # TODO(steving) Remove this dep once the type reporting is delegated to Expr impls..
        ":expr_impls",
        ":stmt",
        ":stmt_list_node",
        "//:apache_commons_text",
        "//:guava",
        "//src/java/com/claro/examples/calculator_example:calculator_parser_exception",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:scoped_heap",
    ],
)
java_library(
    name = "stmt",
    srcs = ["Stmt.java"],
    deps = [
        ":node",
        "//:guava",
    ],
)

java_library(
    name = "program_node",
    srcs = ["ProgramNode.java"],
    deps = [
        ":node",
        ":stmt_list_node",
        "//:guava",
        "//src/java/com/claro/examples/calculator_example:calculator_parser_exception",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:scoped_heap",
    ],
)

java_library(
    name = "node",
    srcs = [
        "Node.java",
        "Target.java",
    ],
    deps = [
        "//:guava",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:scoped_heap",
    ],
)
