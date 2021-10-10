package(
    default_visibility = [
        "//src/java/com/claro/intermediate_representation:__subpackages__",
        "//src/java/com/claro/calculator_programs:__pkg__",
    ]
)


java_library(
    name = "structs",
    srcs = glob(["*.java"]),
    deps = [
        "//:guava",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/intermediate_representation/types/impls/user_defined_impls:user_defined_impls",
    ],
)