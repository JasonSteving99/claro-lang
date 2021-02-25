package(default_visibility = ["//visibility:public"])

filegroup(
    name = "calculator_programs",
    srcs = glob(["*.calculator"]),
)

load("//src/java/com/claro/examples/calculator_example:java_calculator.bzl",
     "java_calculator_library",
     "java_calculator_binary")

java_calculator_binary(
    name = "conditions_compiled_calculator_image",
    srcs = [":conditions_compiled_calculator"],
    java_name = "Conditions",
)

java_calculator_library(
    name = "conditions_compiled_calculator",
    src = "conditions.calculator",
    java_name = "Conditions",
)