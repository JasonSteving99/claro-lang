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
    # TODO(steving) Fix Riju configuration so that we don't always need to have this file named Conditions.
    src = "Conditions.claro",
)

claro_binary(
    name = "contracts_compiled_claro_image",
    srcs = [":contracts_compiled_claro"],
    java_name = "contracts",
)

claro_library(
    name = "contracts_compiled_claro",
    src = "contracts.claro",
    debug = True,
)

claro_binary(
    name = "exec_compiled_claro_image",
    srcs = [":exec_compiled_claro"],
    java_name = "exec",
)

claro_library(
    name = "exec_compiled_claro",
    src = "exec.claro",
)

claro_binary(
    name = "format_strings_compiled_claro_image",
    srcs = [":format_strings_compiled_claro"],
    java_name = "format_strings",
)

claro_library(
    name = "format_strings_compiled_claro",
    src = "format_strings.claro",
)

claro_binary(
    name = "graphs_compiled_claro_image",
    srcs = [":graphs_compiled_claro"],
    java_name = "graphs",
)

claro_library(
    name = "graphs_compiled_claro",
    src = "graphs.claro",
)

claro_binary(
    name = "keyword_generics_compiled_claro_image",
    srcs = [":keyword_generics_compiled_claro"],
    java_name = "keyword_generics",
)

claro_library(
    name = "keyword_generics_compiled_claro",
    src = "keyword_generics.claro",
#    debug = True,
)

claro_binary(
    name = "lambdas_compiled_claro_image",
    srcs = [":lambdas_compiled_claro"],
    java_name = "lambdas",
)

claro_library(
    name = "lambdas_compiled_claro",
    src = "lambdas.claro",
)

claro_binary(
    name = "list_compiled_claro_image",
    srcs = [":list_compiled_claro"],
    java_name = "list",
)

claro_library(
    name = "list_compiled_claro",
    src = "list.claro",
)

claro_binary(
    name = "modules_compiled_claro_image",
    srcs = [":modules_compiled_claro"],
    java_name = "modules",
)

claro_library(
    name = "modules_compiled_claro",
    src = "modules.claro",
)

claro_binary(
    name = "piping_compiled_claro_image",
    srcs = [":piping_compiled_claro"],
    java_name = "piping",
)

claro_library(
    name = "piping_compiled_claro",
    src = "piping.claro",
)

claro_binary(
    name = "procedures_compiled_claro_image",
    srcs = [":procedures_compiled_claro"],
    java_name = "procedures",
)

claro_library(
    name = "procedures_compiled_claro",
    src = "procedures.claro",
)

claro_binary(
    name = "recursion_compiled_claro_image",
    srcs = [":recursion_compiled_claro"],
    java_name = "recursion",
)

claro_library(
    name = "recursion_compiled_claro",
    src = "recursion.claro",
)

claro_binary(
    name = "structs_compiled_claro_image",
    srcs = [":structs_compiled_claro"],
    java_name = "structs",
)

claro_library(
    name = "structs_compiled_claro",
    src = "structs.claro",
)

claro_binary(
    name = "tuples_compiled_claro_image",
    srcs = [":tuples_compiled_claro"],
    java_name = "tuples",
)

claro_library(
    name = "tuples_compiled_claro",
    src = "tuples.claro",
)

claro_binary(
    name = "types_compiled_claro_image",
    srcs = [":types_compiled_claro"],
    java_name = "types",
)

claro_library(
    name = "types_compiled_claro",
    src = "types.claro",
)

claro_binary(
    name = "while_test_compiled_claro_image",
    srcs = [":while_test_compiled_claro"],
    java_name = "while_test",
)

claro_library(
    name = "while_test_compiled_claro",
    src = "while_test.claro",
)
