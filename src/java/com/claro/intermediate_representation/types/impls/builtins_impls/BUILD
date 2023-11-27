package(
    default_visibility = [
        "//visibility:public",
    ]
)

java_library(
    name = "builtins_impls",
    srcs = glob(["*.java"]),
    deps = [
        "//:guava",
        "//src/java/com/claro/intermediate_representation/types",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types/impls:claro_type_implementation",
    ],
)