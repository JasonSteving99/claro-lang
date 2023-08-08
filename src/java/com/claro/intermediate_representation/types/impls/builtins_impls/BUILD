package(
    default_visibility = [
        "//src/java/com/claro/claro_programs:__subpackages__",
        "//src/java/com/claro/compiler_backends/interpreted:__pkg__",
        "//src/java/com/claro/intermediate_representation:__subpackages__",
        "//src/java/com/claro/runtime_utilities:__pkg__",
        "//src/java/com/claro/stdlib/claro:__subpackages__",
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