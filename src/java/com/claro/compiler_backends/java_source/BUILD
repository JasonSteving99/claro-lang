package(default_visibility = ["//visibility:public"])

java_library(
    name = "java_source_compiler_backend_cli_options",
    srcs = ["JavaSourceCompilerBackendCLIOptions.java"],
    deps = [
      "//:google-options",
    ],
    visibility = ["//tools/clarodocs/generator:__pkg__"],
)

java_library(
    name = "java_source",
    srcs = [
        "JavaSourceCompilerBackend.java",
    ],
    deps = [
      ":java_source_compiler_backend_cli_options",
      "//:autovalue",
      "//:google-options",
      "//:guava",
      "//:protobuf",
      "//src/java/com/claro:claro_java_parser",
      "//src/java/com/claro:claro_parser_exception",
      "//src/java/com/claro/compiler_backends:compiler_backend",
      "//src/java/com/claro/compiler_backends:parser_util",
      "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
      "//src/java/com/claro/compiler_backends/java_source/monomorphization:monomorphization_coordinator",
      "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_protos:ipc_messages_java_proto",
      "//src/java/com/claro/intermediate_representation:module_node",
      "//src/java/com/claro/intermediate_representation:node",
      "//src/java/com/claro/intermediate_representation:program_node",
      "//src/java/com/claro/intermediate_representation/expressions:expr",
      "//src/java/com/claro/intermediate_representation/expressions/term:term_impls",
      "//src/java/com/claro/intermediate_representation/statements:stmt",
      "//src/java/com/claro/intermediate_representation/statements:stmt_impls",
      "//src/java/com/claro/intermediate_representation/statements:stmt_list_node",
      "//src/java/com/claro/intermediate_representation/statements/contracts",
      "//src/java/com/claro/intermediate_representation/statements/contracts:contract_procedure_signature",
      "//src/java/com/claro/intermediate_representation/statements/user_defined_type_def_stmts",
      "//src/java/com/claro/intermediate_representation/types",
      "//src/java/com/claro/intermediate_representation/types:type",
      "//src/java/com/claro/intermediate_representation/types:type_provider",
      "//src/java/com/claro/internal_static_state",
      "//src/java/com/claro/module_system:module_api_parser_util",
      "//src/java/com/claro/module_system/module_serialization/proto:serialized_claro_module_java_proto",
      "//src/java/com/claro/module_system/module_serialization/proto/claro_types:claro_types_java_proto",
      "//src/java/com/claro/stdlib",
    ],
)