package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/intermediate_representation:__pkg__",
        "//src/java/com/claro/intermediate_representation/expressions:__subpackages__",
        "//src/java/com/claro/intermediate_representation/statements:__pkg__",
        "//src/java/com/claro/runtime_utilities/injector:__pkg__",
    ]
)


java_library(
    name = "term_impls",
    srcs = glob(
        ["*Term.java"],
        exclude = ["Term.java"],
    ),
    deps = [
        ":term",
        "//:guava",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/expressions/procedures/functions:functions",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/internal_static_state",
    ],
)

java_library(
    name = "term",
    srcs = ["Term.java"],
    deps = [
        "//:guava",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
    ],
)