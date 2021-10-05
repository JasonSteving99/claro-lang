package(
    default_visibility = [
        "//src/java/com/claro/intermediate_representation:__subpackages__",
        "//src/java/com/claro/calculator_programs:__pkg__",
        "//src/java/com/claro/compiler_backends/interpreted:__pkg__",
        "//src/java/com/claro/runtime_utilities:__pkg__",
    ]
)

java_library(
    name = "builtins_impls",
    srcs = glob(["*.java"]),
    deps = [
        "//:guava",
        "//src/java/com/claro/intermediate_representation/types:type",
    ],
)