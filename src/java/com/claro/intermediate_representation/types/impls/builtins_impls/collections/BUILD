package(
    default_visibility = [
        "//visibility:public",
    ]
)

java_library(
    name = "collection_interface",
    srcs = glob(["Collection.java"]),
    deps = [
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls:builtins_impls",
    ],
)

java_library(
    name = "collections_impls",
    srcs = glob(["*.java"]),
    deps = [
        "//:guava",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls:builtins_impls",
        "//src/java/com/claro/intermediate_representation/types",
        "//src/java/com/claro/intermediate_representation/types:type",
    ],
)
