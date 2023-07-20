java_library(
    name = "internal_static_state",
    visibility = [
        "//src/java/com/claro/compiler_backends/java_source:__pkg__",
        "//src/java/com/claro/intermediate_representation:__subpackages__",
        "//src/java/com/claro/module_system:__subpackages__",
    ],
    srcs = ["InternalStaticStateUtil.java"],
    deps = [
        "//:guava",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
    ]
)
