load("//src/java/com/claro:claro_build_rules_internal.bzl", "bootstrapped_claro_module_internal", "bootstrapped_claro_module", "CLARO_BUILTIN_JAVA_DEPS")

package(
    default_visibility = [
        "//src/java/com/claro/compiler_backends/java_source/monomorphization:__subpackages__",
    ]
)

bootstrapped_claro_module(
    name = "monomorphization_ipc",
    module_api_file = "monomorphization_IPC.claro_module_api",
    srcs = ["monomorphization_IPC.claro"],
    optional_stdlib_deps = ["http"],
    deps = {
        "MonomorphizationHackUtils": ":monomorphization_IPC_hack_utils",
    },
)

bootstrapped_claro_module_internal(
    name = "monomorphization_IPC_hack_utils",
    module_api_file = "monomorphization_IPC_hack_utils.claro_module_api",
    srcs = [
        "monomorphization_IPC_hack_utils.claro_internal",
    ],
    exported_custom_java_deps = [
        ":monomorphization_request_processing",
    ],
)

java_library(
    name = "monomorphization_request_processing",
    srcs = ["MonomorphizationRequestProcessing.java"],
    deps = [
        "//:guava",
        "//:protobuf",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_protos:ipc_messages_java_proto",
        "//src/java/com/claro/module_system/module_serialization/proto/claro_types:claro_types_java_proto",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/intermediate_representation/statements/contracts",
    ],
)
