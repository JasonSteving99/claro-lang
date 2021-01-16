package(default_visibility = ["//visibility:public"])

load(":java_calculator.bzl", "gen_calculator_compiler", "java_calculator_library", "java_calculator_binary")

java_calculator_binary(
    name = "first_compiled_calculator_binary",
    srcs = [":first_compiled_calculator"],
    java_name = "First",
)

java_calculator_library(
    name = "first_compiled_calculator",
    src = "//src/java/com/claro/examples/calculator_example/calculator_programs:first.calculator",
    java_name = "First",
)

java_calculator_binary(
    name = "second_compiled_calculator_binary",
    srcs = [":second_compiled_calculator"],
    java_name = "Second",
)

java_calculator_library(
    name = "second_compiled_calculator",
    src = "//src/java/com/claro/examples/calculator_example/calculator_programs:second.calculator",
    java_name = "Second",
)

gen_calculator_compiler()
