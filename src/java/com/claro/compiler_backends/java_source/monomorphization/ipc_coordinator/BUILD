load("//src/java/com/claro:claro_build_rules_internal.bzl", "bootstrapped_claro_module_internal", "bootstrapped_claro_module", "CLARO_BUILTIN_JAVA_DEPS")

package(
    default_visibility = [
        "//src/java/com/claro/compiler_backends/java_source/monomorphization:__pkg__",
    ]
)

bootstrapped_claro_module(
    name = "monomorphization_ipc_coordinator",
    module_api_file = "monomorphization_IPC_coordinator.claro_module_api",
    srcs = ["monomorphization_IPC_coordinator.claro"],
    optional_stdlib_deps = ["http"],
    deps = {
        "MonomorphizationHackUtils": ":monomorphization_IPC_coordinator_hack_utils",
    },
)

bootstrapped_claro_module_internal(
    name = "monomorphization_IPC_coordinator_hack_utils",
    module_api_file = "monomorphization_IPC_coordinator_hack_utils.claro_module_api",
    srcs = [
        "monomorphization_IPC_coordinator_hack_utils.claro_internal",
    ],
    exported_custom_java_deps = [
        ":subprocess_registration",
    ],
)

java_library(
    name = "subprocess_registration",
    srcs = ["SubprocessRegistration.java"],
    deps = [
        "//:autovalue",
        "//:guava",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc:monomorphization_ipc_compiled_claro_module_java_lib",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/futures:ClaroFuture",
    ],
)
