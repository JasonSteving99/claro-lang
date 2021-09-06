package(
    default_visibility = [
        "//src/java/com/claro/examples/calculator_example/intermediate_representation:__subpackages__",
        "//src/java/com/claro/examples/calculator_example/calculator_programs:__pkg__",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:__pkg__",
        "//src/java/com/claro/examples/calculator_example/runtime_utilities:__pkg__",
    ]
)

java_library(
    name = "builtins_impls",
    srcs = glob(["*.java"]),
    deps = [
        "//:guava",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types:type",
    ],
)