load("@rules_proto//proto:defs.bzl", "proto_library")

java_proto_library(
    name = "serialized_claro_module_java_proto",
    deps = [":serialized_claro_module_proto"],
    visibility = [
        "//src/java/com/claro/compiler_backends:__subpackages__",
    ]
)

proto_library(
    name = "serialized_claro_module_proto",
    srcs = ["SerializedClaroModule.proto"],
)