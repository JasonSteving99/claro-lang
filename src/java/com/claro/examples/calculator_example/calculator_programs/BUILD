package(default_visibility = ["//visibility:public"])

# TODO(steving) Eventually cleanup and move those build targets in here as well.
filegroup(
    name = "calculator_programs",
    srcs = ["first.calculator", "second.calculator"],
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


java_calculator_binary(
    name = "list_compiled_calculator_image",
    srcs = [":list_compiled_calculator"],
    java_name = "List",
)

java_calculator_library(
    name = "list_compiled_calculator",
    src = "list.calculator",
    java_name = "List",
)
java_calculator_binary(
    name = "while_compiled_calculator_image",
    srcs = [":while_compiled_calculator"],
    java_name = "While",
)

java_calculator_library(
    name = "while_compiled_calculator",
    src = "while.calculator",
    java_name = "While",
)
