package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/claro_programs:__subpackages__",
        "//src/java/com/claro/intermediate_representation:__subpackages__",
        "//src/java/com/claro/runtime_utilities:__pkg__",
        "//src/java/com/claro/stdlib/claro:__subpackages__",
    ]
)


java_library(
    name = "structs",
    srcs = glob(["*.java"]),
    deps = [
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls:builtins_impls",
    ],
)