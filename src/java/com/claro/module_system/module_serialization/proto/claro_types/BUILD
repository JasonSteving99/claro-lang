load("@rules_proto//proto:defs.bzl", "proto_library")

java_proto_library(
    name = "claro_types_java_proto",
    deps = [":claro_types_proto"],
    visibility = [
        "//src/java/com/claro/compiler_backends:__subpackages__",
        "//src/java/com/claro/compiler_backends/interpreted:__pkg__",
        "//src/java/com/claro/compiler_backends/java_source/monomorphization:__subpackages__",
        "//src/java/com/claro/intermediate_representation:__pkg__",
        "//src/java/com/claro/intermediate_representation/expressions/procedures/functions:__pkg__",
        "//src/java/com/claro/intermediate_representation/statements:__pkg__",
        "//src/java/com/claro/intermediate_representation/types:__pkg__",
        "//src/java/com/claro/module_system/clarodocs:__subpackages__",
    ]
)

proto_library(
    name = "claro_types_proto",
    srcs = ["ClaroTypes.proto"],
    visibility = [
        "//src/java/com/claro/compiler_backends/java_source/monomorphization:__subpackages__",
        "//src/java/com/claro/module_system/module_serialization/proto:__pkg__",
    ]
)
