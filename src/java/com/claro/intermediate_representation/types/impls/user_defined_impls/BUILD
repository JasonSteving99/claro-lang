package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/claro_programs:__subpackages__",
        "//src/java/com/claro/intermediate_representation:__subpackages__",
        "//src/java/com/claro/runtime_utilities/http:__subpackages__",
        "//src/java/com/claro/runtime_utilities:__pkg__",
    ]
)

java_library(
    name = "user_defined_impls",
    srcs = glob(["*.java"]),
    deps = [
        "//:guava",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/intermediate_representation/types/impls:claro_type_implementation",
    ],
)
