package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/compiler_backends:__subpackages__",
        "//src/java/com/claro/intermediate_representation/expressions:__subpackages__",
        "//src/java/com/claro/intermediate_representation/statements:__subpackages__",
        "//src/java/com/claro/intermediate_representation/types:__pkg__",
        "//src/java/com/claro/module_system:__subpackages__",
        "//src/java/com/claro/repl_site/server/handlers:__pkg__",
        "//src/java/com/claro/stdlib:__subpackages__",
    ]
)

java_library(
    name = "program_node",
    srcs = ["ProgramNode.java"],
    deps = [
        ":module_node",
        ":node",
        "//:guava",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization:monomorphization_coordinator",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_protos:ipc_messages_java_proto",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/expressions/procedures/functions:structural_concrete_generic_type_validation_util",
        "//src/java/com/claro/intermediate_representation/statements:stmt",
        "//src/java/com/claro/intermediate_representation/statements:stmt_impls",
        "//src/java/com/claro/intermediate_representation/statements:stmt_list_node",
        "//src/java/com/claro/intermediate_representation/statements/contracts",
        "//src/java/com/claro/intermediate_representation/statements/user_defined_type_def_stmts",
        "//src/java/com/claro/intermediate_representation/types",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/internal_static_state",
    ],
)

java_library(
    name = "module_node",
    srcs = ["ModuleNode.java"],
    deps = [
        "//:autovalue",
        "//:guava",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation/expressions/term:term_impls",
        "//src/java/com/claro/intermediate_representation/statements:stmt_impls",
        "//src/java/com/claro/intermediate_representation/statements/contracts:contract_procedure_signature",
        "//src/java/com/claro/intermediate_representation/statements/contracts:contracts",
        "//src/java/com/claro/intermediate_representation/statements/user_defined_type_def_stmts",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
        "//src/java/com/claro/intermediate_representation/types",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/internal_static_state",
        "//src/java/com/claro/module_system/module_serialization/proto:serialized_claro_module_java_proto",
        "//src/java/com/claro/module_system/module_serialization/proto/claro_types:claro_types_java_proto",
    ],
)

java_library(
    name = "node",
    srcs = [
        "Node.java",
        "Target.java",
    ],
    deps = [
        "//:guava",
        "//:autovalue",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
    ],
)
