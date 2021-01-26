package(default_visibility = ["//src/java/com/claro/examples/calculator_example:__pkg__"])

java_library(
    name = "expr_impls",
    srcs = [
        "InputExpr.java",
        "ParenthesizedExpr.java",
    ],
    deps = [
        ":bool_expr_impls",
        ":expr",
        ":numeric_expr_impls",
        "//:guava",
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
    name = "node",
    srcs = [
        "Node.java",
        "Target.java",
    ],
    deps = ["//:guava"],
)
