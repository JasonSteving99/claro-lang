package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/compiler_backends/java_source:__subpackages__",
        "//src/java/com/claro/intermediate_representation:__subpackages__",
        "//src/java/com/claro/module_system:__subpackages__",
    ]
)

java_library(
    name = "contracts",
    srcs = glob(["*Stmt.java"], exclude = ["ContractProcedureSignatureDefinitionStmt.java"]),
    exports = [":contract_procedure_signature"], # Export this so that main Claro deps don't notice the split.
    deps = [
        ":contract_procedure_signature",
        "//:autovalue",
        "//:guava",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/expressions/procedures/functions:structural_concrete_generic_type_validation_util",
        "//src/java/com/claro/intermediate_representation/statements:stmt",
        "//src/java/com/claro/intermediate_representation/statements:stmt_list_node",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:concrete_type",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/internal_static_state",
    ],
)

# Separate this out just so that the ModuleApiParser can depend on this alone.
java_library(
    name = "contract_procedure_signature",
    srcs = ["ContractProcedureSignatureDefinitionStmt.java"],
    deps = [
        "//:autovalue",
        "//:guava",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/expressions/procedures/functions:structural_concrete_generic_type_validation_util",
        "//src/java/com/claro/intermediate_representation/statements:stmt",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/internal_static_state",
    ],
)