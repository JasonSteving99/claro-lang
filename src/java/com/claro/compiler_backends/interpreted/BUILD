package(default_visibility = ["//visibility:public"])

load("//src/java/com/claro:claro_build_rules.bzl", "DEFAULT_CLARO_NAME")

java_library(
    name = "interpreter",
    srcs = ["Interpreter.java"],
    deps = [
      ":scoped_heap",
      "//src/java/com/claro:" + DEFAULT_CLARO_NAME + "_java_parser",
      "//src/java/com/claro/compiler_backends:compiler_backend",
      "//src/java/com/claro/intermediate_representation:node",
      "//src/java/com/claro/intermediate_representation:program_node",
    ],
)

# TODO(steving) This is now used by all CompilerBackends. Move out of .../interpreted.
java_library(
    name = "scoped_heap",
    srcs = ["ScopedHeap.java"],
    deps = [
        "//:autovalue",
        "//:guava",
        "//:lombok",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/intermediate_representation/types:base_type",
        "//src/java/com/claro/intermediate_representation/types:type",
    ],
)