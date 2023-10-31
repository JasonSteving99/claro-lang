load("//src/java/com/claro:claro_build_rules_internal.bzl", "bootstrapped_claro_module_internal", "bootstrapped_claro_module", "CLARO_BUILTIN_JAVA_DEPS")

java_library(
    name = "monomorphization_coordinator",
    srcs = ["MonomorphizationCoordinator.java"],
    deps = [
        "@bazel_tools//tools/java/runfiles",
        "//:guava",
        "//:protobuf",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc:main_compilation_unit_monomorphization_ipc_compiled_claro_module_java_lib",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_coordinator:monomorphization_ipc_coordinator_compiled_claro_module_java_lib",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_coordinator:subprocess_registration",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_protos:ipc_messages_java_proto",
        "//src/java/com/claro/module_system/module_serialization/proto/claro_types:claro_types_java_proto",
        "//src/java/com/claro/runtime_utilities",
        "//src/java/com/claro/runtime_utilities/http",
        "//src/java/com/claro/runtime_utilities/http:http_server",
    ],
    # Need this binary in both data and resources as I fallback to the one bundled in the jar if the runfiles one isn't
    # avaialable. This will be the case for the sake of the non-dev usage of the prebuilt compiler outside of this Bazel
    # project. This simply enables non-Claro-compiler-devs to build Claro projects without needing to rebuild the
    # compiler from source every time they do a clean build.
    data = ["dep_module_monomorphization_deploy.jar"],
    resources = ["dep_module_monomorphization_deploy.jar"],
    visibility = [
        "//src/java/com/claro/compiler_backends/java_source:__pkg__",
        "//src/java/com/claro/intermediate_representation:__pkg__",
    ]
)

# I'll leave this implementation around just since it's actually a complex enough multiprocessing architecture that I
# think running the prototype will actually be by far the best way to demonstrate the IPC protocol.
java_binary(
    name = "test_coordinator",
    srcs = ["TestCoordinator.java"],
    main_class = "com.claro.compiler_backends.java_source.monomorphization.TestCoordinator",
    deps = [
        "//:guava",
        "//:protobuf",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc:monomorphization_ipc_compiled_claro_module_java_lib",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_coordinator:monomorphization_ipc_coordinator_compiled_claro_module_java_lib",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_coordinator:subprocess_registration",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_protos:ipc_messages_java_proto",
        "//src/java/com/claro/module_system/module_serialization/proto/claro_types:claro_types_java_proto",
        "//src/java/com/claro/runtime_utilities",
        "//src/java/com/claro/runtime_utilities/http",
        "//src/java/com/claro/runtime_utilities/http:http_server",
    ],
    data = [":dep_module_monomorphization_deploy.jar"],
)

java_binary(
    name = "dep_module_monomorphization",
    srcs = [
        "DepModuleMonomorphization.java",
        "DepModuleMonomorphizationCLIOptions.java",
    ],
    main_class = "com.claro.compiler_backends.java_source.monomorphization.DepModuleMonomorphization",
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

        "//:google-options",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc:monomorphization_ipc_compiled_claro_module_java_lib",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc:monomorphization_request_processing",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_coordinator:monomorphization_ipc_coordinator_compiled_claro_module_java_lib",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc_protos:ipc_messages_java_proto",
        "//src/java/com/claro/module_system/module_serialization/proto:serialized_claro_module_java_proto",
        "//src/java/com/claro/runtime_utilities",
        "//src/java/com/claro/runtime_utilities/http",
        "//src/java/com/claro/runtime_utilities/http:http_server",
    ],
    runtime_deps = [
        # NOTE: This runtime-dep sectiono is not permanently necessary after subsequent releases enable the
        #       "bootstrapping compiler"'s jar to include the up-to-date implementation of the proto definition. This is
        #       instead, quite a hacky workaround that has Bazel somewhat incidentally overwriting Java's PATH to make
        #       the latest local build version take precedence over the version in the bootstrapping jar in deps above.
        #       Make use of this field for deps in the .../monomorphization/* subtree that have been locally changed in
        #       order to avoid needing to go through an entire
#        "//src/java/com/claro/module_system/module_serialization/proto:serialized_claro_module_java_proto",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc:monomorphization_ipc_compiled_claro_module_java_lib",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization/ipc:monomorphization_request_processing"
    ],
    visibility = [
        "//src/java/com/claro:__pkg__",
    ]
)