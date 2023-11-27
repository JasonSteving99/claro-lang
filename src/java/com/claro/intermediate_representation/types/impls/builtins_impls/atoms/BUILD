package(
    default_visibility = [
        "//visibility:public",
    ]
)

java_library(
    name = "atoms",
    srcs = glob(["*.java"]),
    deps = [
        "//:autovalue",
        "//:guava",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls:builtins_impls",
        "//src/java/com/claro/intermediate_representation/types/impls/user_defined_impls",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types",
    ],
)
