package(
    default_visibility = [
        "//src/java/com/claro/claro_programs:__subpackages__",
        "//src/java/com/claro/stdlib:__subpackages__",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/http:__subpackages__",
        "//src/java/com/claro/runtime_utilities/http:__subpackages__",
    ]
)

java_library(
    name = "procedures",
    srcs = glob(["*.java"]),
    deps = [
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls:builtins_impls",
    ],
)
