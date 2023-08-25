package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/claro_programs:__subpackages__",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization:__subpackages__",
        "//src/java/com/claro/intermediate_representation/expressions:__pkg__",
        "//src/java/com/claro/intermediate_representation/statements:__pkg__",
        "//src/java/com/claro/runtime_utilities:__subpackages__",
        "//src/java/com/claro/stdlib/claro:__subpackages__",
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
