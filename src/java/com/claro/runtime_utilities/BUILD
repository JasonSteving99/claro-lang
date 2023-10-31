package(
    default_visibility = [
        "//visibility:public",
    ]
)

java_library(
    name = "runtime_utilities",
    srcs = glob(["*.java"]),
    deps = [
        "//:guava",
        "//src/java/com/claro/stdlib:stdlib_module_registry",
        "//src/java/com/claro/intermediate_representation/types:concrete_type",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/intermediate_representation/types/impls:claro_type_implementation",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/structs",
        "//src/java/com/claro/intermediate_representation/types/impls/user_defined_impls",
    ],
)
