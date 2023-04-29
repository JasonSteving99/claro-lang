package(
    default_visibility = [
        "//src/java/com/claro/claro_programs:__subpackages__",
    ]
)

java_library(
    name = "http",
    srcs = ["$HttpUtil.java"],
    deps = [
        "//:guava",
        "//:okhttp",
        "//:retrofit",
        "//src/java/com/claro/intermediate_representation/types",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/futures:ClaroFuture",
        "//src/java/com/claro/intermediate_representation/types/impls/user_defined_impls:user_defined_impls",
        "//src/java/com/claro/runtime_utilities",
    ],
)