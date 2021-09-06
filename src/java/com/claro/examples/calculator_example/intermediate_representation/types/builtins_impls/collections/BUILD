package(
    default_visibility = [
        "//src/java/com/claro/examples/calculator_example/intermediate_representation:__pkg__",
        "//src/java/com/claro/examples/calculator_example/calculator_programs:__pkg__",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:__pkg__",
    ]
)

java_library(
    name = "collection_interface",
    srcs = glob(["Collection.java"]),
    deps = [
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types/builtins_impls",
    ],
)

java_library(
    name = "collections_impls",
    srcs = glob(["*.java"]),
    deps = [
        "//:guava",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types/builtins_impls",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types:type",
    ],
)
