load("@rules_proto//proto:defs.bzl", "proto_library")

java_proto_library(
    name = "serialized_claro_module_java_proto",
    deps = [":serialized_claro_module_proto"],
    visibility = [
        "//src/java/com/claro/compiler_backends:__subpackages__",
        "//src/java/com/claro/compiler_backends/interpreted:__pkg__",
        "//src/java/com/claro/intermediate_representation:__pkg__",
        "//src/java/com/claro/intermediate_representation/expressions/procedures/functions:__pkg__",
        "//src/java/com/claro/intermediate_representation/expressions/term:__pkg__",
        "//src/java/com/claro/intermediate_representation/statements:__pkg__",
        "//src/java/com/claro/module_system/clarodocs:__subpackages__",
        "//src/java/com/claro/module_system/module_serialization/json:__pkg__",
        "//src/java/com/claro/stdlib/utils:__pkg__",
        "//tools/clarodocs/generator:__pkg__",
    ]
)

proto_library(
    name = "serialized_claro_module_proto",
    srcs = ["SerializedClaroModule.proto"],
    deps = [
        "//src/java/com/claro/module_system/module_serialization/proto/claro_types:claro_types_proto",
    ] )