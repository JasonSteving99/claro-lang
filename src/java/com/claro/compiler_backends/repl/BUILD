package(default_visibility = ["//visibility:public"])

java_library(
    name = "repl",
    srcs = ["Repl.java"],
    deps = [
      "//:guava",
      "//src/java/com/claro:claro_parser_exception",
      "//src/java/com/claro:claro_java_parser",
      "//src/java/com/claro/compiler_backends:compiler_backend",
      "//src/java/com/claro/compiler_backends:parser_util",
      "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
      "//src/java/com/claro/compiler_backends/repl/repl_terminal:repl_terminal",
      "//src/java/com/claro/intermediate_representation/expressions:expr",
      "//src/java/com/claro/intermediate_representation:node",
      "//src/java/com/claro/intermediate_representation:program_node",
      "//src/java/com/claro/intermediate_representation/statements:stmt",
      "//src/java/com/claro/stdlib",
    ],
)