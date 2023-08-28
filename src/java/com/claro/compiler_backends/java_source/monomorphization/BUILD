load("//src/java/com/claro:claro_build_rules_internal.bzl", "claro_module_internal", "claro_module")

# TODO(steving) DELETE THIS AND INCORPORATE INTO COMPILER ITSELF.
java_binary(
    name = "test_coordinator",
    srcs = ["TestCoordinator.java"],
    main_class = "com.claro.compiler_backends.java_source.monomorphization.TestCoordinator",
    deps = [
        ":monomorphization_ipc_compiled_claro_module_java_lib",
        ":monomorphization_ipc_coordinator_compiled_claro_module_java_lib",
        ":subprocess_registration",
        "//:guava",
        "//:protobuf",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_protos:ipc_messages_java_proto",
        "//src/java/com/claro/module_system/module_serialization/proto/claro_types:claro_types_java_proto",
        "//src/java/com/claro/runtime_utilities",
        "//src/java/com/claro/runtime_utilities/http",
        "//src/java/com/claro/runtime_utilities/http:http_server",
    ]
)

java_binary(
    name = "dep_module_monomorphization",
    srcs = [
        "DepModuleMonomorphization.java",
        "DepModuleMonomorphizationCLIOptions.java",
    ],
    main_class = "com.claro.compiler_backends.java_source.monomorphization.DepModuleMonomorphization",
    deps = [
        ":monomorphization_ipc_compiled_claro_module_java_lib",
        ":monomorphization_ipc_coordinator_compiled_claro_module_java_lib",
        "//:google-options",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_protos:ipc_messages_java_proto",
        "//src/java/com/claro/runtime_utilities",
        "//src/java/com/claro/runtime_utilities/http",
        "//src/java/com/claro/runtime_utilities/http:http_server",
    ],
    args = ["--coordinator_port", "8080", "--dep_module_file_path", "foo/bar"]
)


claro_module(
    name = "monomorphization_ipc",
    module_api_file = "monomorphization_IPC.claro_module_api",
    srcs = ["monomorphization_IPC.claro"],
    optional_stdlib_deps = ["http"],
    deps = {
        "MonomorphizationHackUtils": ":monomorphization_IPC_hack_utils",
    },
)

claro_module(
    name = "monomorphization_ipc_coordinator",
    module_api_file = "monomorphization_IPC_coordinator.claro_module_api",
    srcs = ["monomorphization_IPC_coordinator.claro"],
    optional_stdlib_deps = ["http"],
    deps = {
        "MonomorphizationHackUtils": ":monomorphization_IPC_coordinator_hack_utils",
    },
)

claro_module_internal(
    name = "monomorphization_IPC_hack_utils",
    module_api_file = "monomorphization_IPC_hack_utils.claro_module_api",
    srcs = [
        "monomorphization_IPC_hack_utils.claro_internal",
    ],
    exported_custom_java_deps = [
        ":monomorphization_request_processing",
    ]
)

claro_module_internal(
    name = "monomorphization_IPC_coordinator_hack_utils",
    module_api_file = "monomorphization_IPC_coordinator_hack_utils.claro_module_api",
    srcs = [
        "monomorphization_IPC_coordinator_hack_utils.claro_internal",
    ],
    exported_custom_java_deps = [
        ":subprocess_registration",
    ]
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

java_library(
    name = "subprocess_registration",
    srcs = ["SubprocessRegistration.java"],
    deps = [
        "//:autovalue",
        "//:guava",
        ":monomorphization_ipc_compiled_claro_module_java_lib",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/futures:ClaroFuture",
    ]
)
