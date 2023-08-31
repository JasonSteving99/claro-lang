package(
    default_visibility = [
        "//src/java/com/claro/claro_programs:__subpackages__",
        "//src/java/com/claro/compiler_backends:__subpackages__",
        "//src/java/com/claro/intermediate_representation:__pkg__",
        "//src/java/com/claro/stdlib:__subpackages__",
    ]
)

java_library(
    name = "stdlib",
    srcs = glob(["*.java"], exclude = ["StdLibModuleRegistry.java"]),
    deps = [
        "//:guava",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/statements:stmt",
        "//src/java/com/claro/intermediate_representation/statements/user_defined_type_def_stmts",
        "//src/java/com/claro/intermediate_representation/types",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/futures:ClaroFuture",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/procedures",
        "//src/java/com/claro/stdlib:stdlib_module_registry",
        "//src/java/com/claro/stdlib/http",
    ],
)

java_library(
    name = "module_util",
    srcs = ["StdLibModuleUtil.java"],
    deps = [
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
    ],
    visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/intermediate_representation/expressions:__pkg__",
    ],
)

java_library(
    name = "stdlib_module_registry",
    srcs = ["StdLibModuleRegistry.java"],
    visibility = [
        "//src/java/com/claro/compiler_backends:__subpackages__",
        "//src/java/com/claro/intermediate_representation:__subpackages__",
        "//src/java/com/claro/module_system:__subpackages__",
        "//src/java/com/claro/runtime_utilities:__subpackages__",
        "//src/java/com/claro/stdlib:__pkg__",
    ]
)