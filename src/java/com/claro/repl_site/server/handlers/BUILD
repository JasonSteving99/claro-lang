package(default_visibility = ["//src/java/com/claro/repl_site/server:__pkg__"])

load("//src/java/com/claro:claro_build_rules.bzl", "DEFAULT_CLARO_NAME")

java_library(
  name = "handlers",
  srcs = glob(["*Handler.java"]),
  deps = [
    "//:javalin",
    "//src/java/com/claro:" + DEFAULT_CLARO_NAME + "_java_parser",
    "//src/java/com/claro:claro_parser_exception",
    "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
    "//src/java/com/claro/intermediate_representation:program_node",
    "//src/java/com/claro/intermediate_representation:node",
    "//src/java/com/claro/compiler_backends:compiler_backend",
  ],
)
