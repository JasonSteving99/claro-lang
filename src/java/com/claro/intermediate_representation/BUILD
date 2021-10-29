package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/compiler_backends:__subpackages__",
        "//src/java/com/claro/intermediate_representation/expressions:__subpackages__",
        "//src/java/com/claro/intermediate_representation/statements:__subpackages__",
        "//src/java/com/claro/intermediate_representation/types:__pkg__",
        "//src/java/com/claro/repl_site/server/handlers:__pkg__",
    ]
)

java_library(
    name = "program_node",
    srcs = ["ProgramNode.java"],
    deps = [
        ":node",
        "//:guava",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation/statements:stmt",
        "//src/java/com/claro/intermediate_representation/statements:stmt_list_node",
        "//src/java/com/claro/intermediate_representation/statements/user_defined_type_def_stmts",
        "//src/java/com/claro/intermediate_representation/types",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
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
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
    ],
)
