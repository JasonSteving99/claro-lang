package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/claro_programs:__subpackages__",
        "//src/java/com/claro/compiler_backends/interpreted:__pkg__",
        "//src/java/com/claro/intermediate_representation/expressions:__pkg__",
        "//src/java/com/claro/intermediate_representation/statements:__pkg__",
        "//src/java/com/claro/runtime_utilities:__subpackages__",
    ]
)

java_library(
    name = "ClaroFuture",
    srcs = glob(["ClaroFuture.java"]),
    deps = [
        "//:guava",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls:builtins_impls",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types",
    ],
)
