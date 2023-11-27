load("//src/java/com/claro:claro_build_rules_internal.bzl", "bootstrapped_claro_module_internal", "bootstrapped_claro_module", "CLARO_BUILTIN_JAVA_DEPS")

package(
    default_visibility = [
        "//src/java/com/claro/compiler_backends/java_source/monomorphization:__subpackages__",
    ]
)

########################################################################################################################
# BEGIN monomorphization_IPC TARGETS:
########################################################################################################################
# Use this first target for the Dep Module Monomorphization case where you'll actually need to call back into the
# bootstrapping compiler.
bootstrapped_claro_module(
    name = "monomorphization_ipc",
    module_api_file = "monomorphization_IPC.claro_module_api",
    srcs = ["monomorphization_IPC.claro"],
    optional_stdlib_deps = ["http"],
    deps = {
        "MonomorphizationHackUtils": ":monomorphization_IPC_hack_utils",
    },
)
# Use this second target in the build of the main compilation unit compiler since it should have no dep back into the
# bootstrapping compiler.
bootstrapped_claro_module(
    name = "main_compilation_unit_monomorphization_ipc",
    module_api_file = "monomorphization_IPC.claro_module_api",
    srcs = ["monomorphization_IPC.claro"],
    optional_stdlib_deps = ["http"],
    deps = {
        "MonomorphizationHackUtils": ":DUMMY_monomorphization_IPC_hack_utils",
    },
)
########################################################################################################################
# END monomorphization_IPC TARGETS:
########################################################################################################################


########################################################################################################################
# BEGIN monomorphization_IPC_hack_utils TARGETS:
########################################################################################################################
bootstrapped_claro_module_internal(
    name = "monomorphization_IPC_hack_utils",
    module_api_file = "monomorphization_IPC_hack_utils.claro_module_api",
    srcs = ["monomorphization_IPC_hack_utils.claro_internal"],
    exported_custom_java_deps = [
        ":monomorphization_request_processing",
    ],
)
bootstrapped_claro_module_internal(
    name = "DUMMY_monomorphization_IPC_hack_utils",
    module_api_file = "monomorphization_IPC_hack_utils.claro_module_api",
    srcs = ["monomorphization_IPC_hack_utils_DUMMY.claro_internal"],
)
########################################################################################################################
# END monomorphization_IPC_hack_utils TARGETS:
########################################################################################################################

java_library(
    name = "monomorphization_request_processing",
    srcs = ["MonomorphizationRequestProcessing.java"],
    deps = [
        # Unfortunately, there's no clear way to get a dep on ClaroCompilerMain.java w/o creating a circular dep that
        # Bazel would be unable to handle. This is somewhat intrinsically related to the problem at hand, so I'm not
        # sure that there's anything to be done about this other than BEING VERY WELL AWARE that the dep module
        # monomorphization subprocesses must run the "bootstrapping" version of the compiler rather than the latest
        # in-development version. This should be fine, although it does mean that any changes to the interprocess
        # communication must be arbitrated through a multi-step release upgrade cycle as the in-development version of
        # the compiler (which is running the monomorphization coordinator) will receive updates that the "bootstrapping"
        # compiler won't get yet until a new release is made.
        "//:bootstrapping_claro_compiler_import",

        "//:guava",
        "//:protobuf",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_protos:ipc_messages_java_proto",
        "//src/java/com/claro/module_system/module_serialization/proto/claro_types:claro_types_java_proto",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/intermediate_representation:node",
        "//src/java/com/claro/intermediate_representation/statements/contracts",
    ],
    runtime_deps = [
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_protos:ipc_messages_java_proto",
    ],
    visibility = [
        "//src/java/com/claro/compiler_backends/java_source/monomorphization:__pkg__",
    ]
)
