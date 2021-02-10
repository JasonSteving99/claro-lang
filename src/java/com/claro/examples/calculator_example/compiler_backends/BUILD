package(
    default_visibility = [
        "//src/java/com/claro/examples/calculator_example/compiler_backends:__subpackages__",
    ]
)

load("//src/java/com/claro/examples/calculator_example:java_calculator.bzl", "DEFAULT_CALCULATOR_NAME")

java_library(
    name = "compiler_backend",
    srcs = glob(["*.java"]),
    deps = [
      "//src/java/com/claro/examples/calculator_example:" + DEFAULT_CALCULATOR_NAME + "_java_parser",
    ]
)
