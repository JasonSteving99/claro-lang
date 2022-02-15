package(default_visibility = ["//visibility:public"])

load("//src/java/com/claro:claro_build_rules.bzl", "DEFAULT_CLARO_NAME")

java_library(
    name = "repl",
    srcs = ["Repl.java"],
    deps = [
      "//src/java/com/claro:claro_parser_exception",
      "//src/java/com/claro:" + DEFAULT_CLARO_NAME + "_java_parser",
      "//src/java/com/claro/compiler_backends:compiler_backend",
      "//src/java/com/claro/compiler_backends:parser_util",
      "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
      "//src/java/com/claro/compiler_backends/repl/repl_terminal:repl_terminal",
      "//src/java/com/claro/intermediate_representation:node",
      "//src/java/com/claro/intermediate_representation:program_node",
      "//src/java/com/claro/stdlib",
    ],
)