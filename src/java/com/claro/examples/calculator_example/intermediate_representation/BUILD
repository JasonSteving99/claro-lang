package(default_visibility = ["//src/java/com/claro/examples/calculator_example:__pkg__"])

java_library(
    name = "expr",
    srcs = ["Expr.java"],
    deps = [
        ":node",
        "//:guava",
    ],
)

java_library(
    name = "float_term",
    srcs = ["FloatTerm.java"],
    deps = [
        ":term",
    ],
)

java_library(
    name = "string_term",
    srcs = ["StringTerm.java"],
    deps = [
        ":term",
    ],
)

java_library(
    name = "integer_term",
    srcs = ["IntegerTerm.java"],
    deps = [
        ":term",
    ],
)

java_library(
    name = "term",
    srcs = ["Term.java"],
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
