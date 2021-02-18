package(default_visibility = ["//visibility:public"])

load("//src/java/com/claro/examples/calculator_example:java_calculator.bzl", "DEFAULT_CALCULATOR_NAME")

java_library(
    name = "repl",
    srcs = ["Repl.java"],
    deps = [
      ":repl_terminal",
      "//src/java/com/claro/examples/calculator_example:calculator_parser_exception",
      "//src/java/com/claro/examples/calculator_example:" + DEFAULT_CALCULATOR_NAME + "_java_parser",
      "//src/java/com/claro/examples/calculator_example/compiler_backends:compiler_backend",
      "//src/java/com/claro/examples/calculator_example/intermediate_representation:node",
      "//src/java/com/claro/examples/calculator_example/intermediate_representation:program_node",
    ],
)

java_library(
    name = "repl_terminal",
    srcs = ["ReplTerminal.java"],
    deps = ["//:lanterna"],
)