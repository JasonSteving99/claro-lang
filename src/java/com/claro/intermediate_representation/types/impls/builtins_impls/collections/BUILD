package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/claro_programs:__subpackages__",
        "//src/java/com/claro/compiler_backends/interpreted:__pkg__",
        "//src/java/com/claro/intermediate_representation/expressions:__pkg__",
        "//src/java/com/claro/intermediate_representation/statements:__pkg__",
        "//src/java/com/claro/stdlib/claro:__subpackages__",
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
