package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/compiler_backends:__subpackages__",
        "//src/java/com/claro/intermediate_representation:__subpackages__",
        "//src/java/com/claro/intermediate_representation/expressions:__pkg__",
        "//src/java/com/claro/module_system:__pkg__",
        "//src/java/com/claro/runtime_utilities/injector:__pkg__",
        "//src/java/com/claro/stdlib:__subpackages__",
    ]
)

java_library(
    name = "stmt_list_node",
    srcs = [
        # We're bringing in all of these classes other than StmtListNode here because we need a Java-supported
        # circular dependency from StmtListNode <-> ProcedureDefinitionStmt, but Bazel can't handle that since
        # it has no way of knowing that it's valid.
        "DeclarationStmt.java",
        "ProcedureDefinitionStmt.java",
        "ConsumerFunctionDefinitionStmt.java",
        "ReturnStmt.java",
        "StmtListNode.java",
        "UsingBlockStmt.java",
        "ModuleDefinitionStmt.java",
        "BindStmt.java",
        "AutomaticErrorPropagationStmt.java",
    ],
    deps = [
        ":stmt",
        "//:guava",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/expressions/term",
        "//src/java/com/claro/intermediate_representation/expressions/procedures/functions:structural_concrete_generic_type_validation_util",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/runtime_utilities",
        "//src/java/com/claro/internal_static_state:internal_static_state",
        "//src/java/com/claro/runtime_utilities/injector",
        "//src/java/com/claro/runtime_utilities/injector:key",
        "//src/java/com/claro/runtime_utilities/injector:injected_key",
    ],
)

java_library(
    name = "stmt_impls",
    srcs = glob(
        ["*Stmt.java"],
        exclude = [
            "Stmt.java",
            # We need a circular dependency that Java can resolve in StmtListNode, but Bazel can't handle it.
            "DeclarationStmt.java",
            "ProcedureDefinitionStmt.java",
            "ReturnStmt.java",

            # Both :stmt_impls and :stmt_list_node need deps on this target.... I'm doing a poor job avoiding circular dep hell...
            "UsingBlockStmt.java",
            "ModuleDefinitionStmt.java",
            "ConsumerFunctionDefinitionStmt.java",
            "BindStmt.java",
            "AutomaticErrorPropagationStmt.java",
        ]
    ),
    deps = [
        ":stmt",
        ":stmt_list_node",
        "//:apache_commons_text",
        "//:autovalue",
        "//:guava",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/expressions:expr_impls",
        "//src/java/com/claro/intermediate_representation/expressions:unwrap_user_defined_type_expr_impl",
        "//src/java/com/claro/intermediate_representation/expressions/bool:bool_expr",
        "//src/java/com/claro/intermediate_representation/expressions/procedures/functions",
        "//src/java/com/claro/intermediate_representation/expressions/procedures/functions:structural_concrete_generic_type_validation_util",
        "//src/java/com/claro/intermediate_representation/expressions/term:term_impls",
        "//src/java/com/claro/intermediate_representation/statements/contracts",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:concrete_type",
        "//src/java/com/claro/intermediate_representation/types:supports_mutable_variant",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/collections:collections_impls",
        "//src/java/com/claro/internal_static_state:internal_static_state",
        "//src/java/com/claro/module_system/module_serialization/proto:serialized_claro_module_java_proto",
        "//src/java/com/claro/runtime_utilities/injector",
        "//src/java/com/claro/runtime_utilities/injector:key",
        "//src/java/com/claro/runtime_utilities/injector:injected_key",
        "//src/java/com/claro/runtime_utilities/injector:injected_key_identifier",
        "//src/java/com/claro/stdlib:stdlib_module_registry",
    ],
)

java_library(
    name = "stmt",
    srcs = ["Stmt.java"],
    deps = [
        "//:guava",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
    ],
)