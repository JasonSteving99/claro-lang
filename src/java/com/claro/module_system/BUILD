package(
    default_visibility = [
        "//src/java/com/claro/compiler_backends:__subpackages__",
    ]
)

load("@jflex_rules//jflex:jflex.bzl", "jflex")
load("@jflex_rules//cup:cup.bzl", "cup")


java_binary(
    name = "test_module_parser",
    srcs = ["TestModuleParser.java"],
    main_class = "com.claro.module_system.TestModuleParser",
    deps = [
        ":module_api_parser_util",
        "//:guava",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:module_node",
        "//src/java/com/claro/intermediate_representation/statements/contracts:contract_procedure_signature",
        "//src/java/com/claro/internal_static_state",
    ],
)

java_library(
    name = "module_api_parser_util",
    srcs = ["ModuleApiParserUtil.java"],
    exports = [
        ":module_api_parser",
    ],
    deps = [
        ":module_api_parser",
    ],
)

java_library(
    name = "module_api_parser",
    srcs = [
        ":gen_module_api_lexer",
        ":gen_module_api_parser",
    ],
    deps = [
        "@jflex_rules//third_party/cup",  # the runtime would be sufficient
        "//:guava",
        "//src/java/com/claro:lexed_value",
        "//src/java/com/claro:scoped_identifier",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation:module_node",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/expressions:expr_impls",
        "//src/java/com/claro/intermediate_representation/expressions/term:term_impls",
        "//src/java/com/claro/intermediate_representation/statements:stmt_impls",
        "//src/java/com/claro/intermediate_representation/statements/contracts:contract_procedure_signature",
        "//src/java/com/claro/intermediate_representation/statements/contracts:contracts",
        "//src/java/com/claro/intermediate_representation/statements/user_defined_type_def_stmts:user_defined_type_def_stmts",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types:supports_mutable_variant",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:types",
    ],
)

cup(
    name = "gen_module_api_parser",
    src = "ModuleApiParser.cup",
    parser = "ModuleApiParser",
    symbols = "Tokens",
)

jflex(
    name = "gen_module_api_lexer",
    srcs = ["ModuleApiLexer.flex"],
    lexer = "ModuleApiLexer",
)
