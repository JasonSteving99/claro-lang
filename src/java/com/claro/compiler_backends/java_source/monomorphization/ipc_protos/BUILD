
java_proto_library(
    name = "ipc_messages_java_proto",
    deps = [":ipc_messages_proto"],
    visibility = [
        "//src/java/com/claro/compiler_backends/java_source/monomorphization:__subpackages__",
    ],
)

proto_library(
    name = "ipc_messages_proto",
    srcs = ["ipc_messages.proto"],
    deps = [
       "//src/java/com/claro/module_system/module_serialization/proto/claro_types:claro_types_proto",
    ]
)