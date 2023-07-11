package(default_visibility = ["//visibility:public"])

load("//src/java/com/claro:claro_build_rules.bzl", "DEFAULT_CLARO_NAME")

java_library(
    name = "java_source",
    srcs = ["JavaSourceCompilerBackend.java"],
    deps = [
      "//:autovalue",
      "//:guava",
      "//src/java/com/claro:" + DEFAULT_CLARO_NAME + "_java_parser",
      "//src/java/com/claro:claro_parser_exception",
      "//src/java/com/claro/compiler_backends:compiler_backend",
      "//src/java/com/claro/compiler_backends:parser_util",
      "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
      "//src/java/com/claro/intermediate_representation:module_node",
      "//src/java/com/claro/intermediate_representation:node",
      "//src/java/com/claro/intermediate_representation:program_node",
      "//src/java/com/claro/intermediate_representation/expressions:expr",
      "//src/java/com/claro/module_system:module_api_parser_util",
      "//src/java/com/claro/stdlib",
    ],
)