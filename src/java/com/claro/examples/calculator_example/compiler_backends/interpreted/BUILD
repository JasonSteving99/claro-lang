package(default_visibility = ["//visibility:public"])

load("//src/java/com/claro/examples/calculator_example:java_calculator.bzl", "DEFAULT_CALCULATOR_NAME")

java_library(
    name = "interpreter",
    srcs = ["Interpreter.java"],
    deps = [
      "//src/java/com/claro/examples/calculator_example:" + DEFAULT_CALCULATOR_NAME + "_java_parser",
      "//src/java/com/claro/examples/calculator_example/compiler_backends:compiler_backend",
      "//src/java/com/claro/examples/calculator_example/intermediate_representation:node",
      "//src/java/com/claro/examples/calculator_example/intermediate_representation:program_node",
      ":scoped_heap",
    ],
)

# TODO(steving) This is now used by all CompilerBackends. Move out of .../interpreted.
java_library(
    name = "scoped_heap",
    srcs = ["ScopedHeap.java"],
    deps = [
        "//:autovalue",
        "//:guava",
        "//src/java/com/claro/examples/calculator_example:calculator_parser_exception",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types:type",
    ],
)