package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/intermediate_representation/expressions:__subpackages__",
        "//src/java/com/claro/intermediate_representation:__pkg__",
        "//src/java/com/claro/intermediate_representation/statements:__pkg__",
        "//src/java/com/claro/intermediate_representation/statements/contracts:__pkg__",
    ]
)


java_library(
    name = "functions",
    srcs = glob(["*FunctionCallExpr.java"]),
    deps = [
        "//:guava",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/expressions/procedures/functions:structural_concrete_generic_type_validation_util",
        "//src/java/com/claro/intermediate_representation/statements:stmt_list_node",
        "//src/java/com/claro/intermediate_representation/statements/contracts",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:concrete_type",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/internal_static_state",
        "//src/java/com/claro/runtime_utilities/injector:key",
    ],
)

java_library(
    name = "structural_concrete_generic_type_validation_util",
    srcs = ["StructuralConcreteGenericTypeValidationUtil.java"],
    deps = [
        "//:guava",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:types",
    ],
)