package(
    default_visibility = [
        "//src/java/com/claro/examples/calculator_example:__pkg__",
        "//src/java/com/claro/examples/calculator_example/compiler_backends:__subpackages__",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/expressions:__subpackages__",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/statements:__subpackages__",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types:__pkg__",
    ]
)

java_library(
    name = "program_node",
    srcs = ["ProgramNode.java"],
    deps = [
        ":node",
        "//:guava",
        "//src/java/com/claro/examples/calculator_example:calculator_parser_exception",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/statements:stmt_list_node",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types:claro_type_exception",
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
        "//:autovalue",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:scoped_heap",
    ],
)
