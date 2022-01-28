package(default_visibility = ["//visibility:public"])

# TODO(steving) Eventually cleanup and move those build targets in here as well.
filegroup(
    name = "claro_programs",
    srcs = ["first.claro", "second.claro"],
)

load("//src/java/com/claro:claro_build_rules.bzl",
     "claro_library",
     "claro_binary")

claro_binary(
    name = "conditions_compiled_claro_image",
    srcs = [":conditions_compiled_claro"],
    java_name = "Conditions",
)

claro_library(
    name = "conditions_compiled_claro",
    src = "conditions.claro",
    java_name = "Conditions",
)

claro_binary(
    name = "lambdas_compiled_claro_image",
    srcs = [":lambdas_compiled_claro"],
    java_name = "Lambdas",
)

claro_library(
    name = "lambdas_compiled_claro",
    src = "lambdas.claro",
    java_name = "Lambdas",
)

claro_binary(
    name = "list_compiled_claro_image",
    srcs = [":list_compiled_claro"],
    java_name = "List",
)

claro_library(
    name = "list_compiled_claro",
    src = "list.claro",
    java_name = "List",
)

claro_binary(
    name = "procedures_compiled_claro_image",
    srcs = [":procedures_compiled_claro"],
    java_name = "Procedures",
)

claro_library(
    name = "procedures_compiled_claro",
    src = "procedures.claro",
    java_name = "Procedures",
)

claro_binary(
    name = "recursion_compiled_claro_image",
    srcs = [":recursion_compiled_claro"],
    java_name = "Recursion",
)

claro_library(
    name = "recursion_compiled_claro",
    src = "recursion.claro",
    java_name = "Recursion",
)

claro_binary(
    name = "structs_compiled_claro_image",
    srcs = [":structs_compiled_claro"],
    java_name = "Structs",
)

claro_library(
    name = "structs_compiled_claro",
    src = "structs.claro",
    java_name = "Structs",
)

claro_binary(
    name = "tuples_compiled_claro_image",
    srcs = [":tuples_compiled_claro"],
    java_name = "Tuples",
)

claro_library(
    name = "tuples_compiled_claro",
    src = "tuples.claro",
    java_name = "Tuples",
)

claro_binary(
    name = "types_compiled_claro_image",
    srcs = [":types_compiled_claro"],
    java_name = "TypesTest",
)

claro_library(
    name = "types_compiled_claro",
    src = "types.claro",
    java_name = "TypesTest",
)

claro_binary(
    name = "while_compiled_claro_image",
    srcs = [":while_compiled_claro"],
    java_name = "While",
)

claro_library(
    name = "while_compiled_claro",
    src = "while.claro",
    java_name = "While",
)
