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
    name = "functions_compiled_calculator_image",
    srcs = [":functions_compiled_calculator"],
    java_name = "Functions",
)

java_calculator_library(
    name = "functions_compiled_calculator",
    src = "functions.calculator",
    java_name = "Functions",
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
    name = "structs_compiled_calculator_image",
    srcs = [":structs_compiled_calculator"],
    java_name = "Structs",
)

java_calculator_library(
    name = "structs_compiled_calculator",
    src = "structs.calculator",
    java_name = "Structs",
)

java_calculator_binary(
    name = "tuples_compiled_calculator_image",
    srcs = [":tuples_compiled_calculator"],
    java_name = "Tuples",
)

java_calculator_library(
    name = "tuples_compiled_calculator",
    src = "tuples.calculator",
    java_name = "Tuples",
)

java_calculator_binary(
    name = "types_compiled_calculator_image",
    srcs = [":types_compiled_calculator"],
    java_name = "Types",
)

java_calculator_library(
    name = "types_compiled_calculator",
    src = "types.calculator",
    java_name = "Types",
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
