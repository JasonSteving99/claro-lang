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
    name = "atoms",
    srcs = [":atoms_lib"],
    java_name = "atoms",
)

claro_library(
    name = "atoms_lib",
    src = "atoms.claro",
)

claro_binary(
    name = "collection_comprehensions",
    srcs = [":collection_comprehensions_lib"],
    java_name = "collection_comprehensions",
)

claro_library(
    name = "collection_comprehensions_lib",
    src = "collection_comprehensions.claro",
)

claro_binary(
    name = "contracts_compiled_claro_image",
    srcs = [":contracts_compiled_claro"],
    java_name = "contracts",
)

claro_library(
    name = "contracts_compiled_claro",
    src = "contracts.claro",
)

claro_binary(
    name = "contracts_implied_types",
    srcs = [":contracts_implied_types_lib"],
    java_name = "contracts_implied_types",
)

claro_library(
    name = "contracts_implied_types_lib",
    src = "contracts_implied_types.claro",
)

claro_binary(
    name = "copy",
    srcs = [":copy_lib"],
    java_name = "copy",
)

claro_library(
    name = "copy_lib",
    src = "copy.claro",
)

claro_binary(
    name = "dynamic_dispatch_compiled_claro_image",
    srcs = [":dynamic_dispatch_compiled_claro"],
    java_name = "dynamic_dispatch",
)

claro_library(
    name = "dynamic_dispatch_compiled_claro",
    src = "dynamic_dispatch.claro",
)

claro_binary(
    name = "error_handling_and_propagation",
    srcs = [":error_handling_and_propagation_lib"],
    java_name = "error_handling_and_propagation",
)

claro_library(
    name = "error_handling_and_propagation_lib",
    src = "error_handling_and_propagation.claro",
)

claro_binary(
    name = "generics_compiled_claro_image",
    srcs = [":generics_compiled_claro"],
    java_name = "generics",
)

claro_library(
    name = "generics_compiled_claro",
    src = "generics.claro",
)

claro_binary(
    name = "generic_return_inference_experimentation_compiled_claro_image",
    srcs = [":generic_return_inference_experimentation_compiled_claro"],
    java_name = "generic_return_inference_experimentation",
)

claro_library(
    name = "generic_return_inference_experimentation_compiled_claro",
    src = "generic_return_inference_experimentation.claro",
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
    name = "for_loops",
    srcs = [":for_loops_lib"],
    java_name = "for_loops",
)

claro_library(
    name = "for_loops_lib",
    src = "for_loops.claro",
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
    name = "http_requests",
    srcs = [":http_requests_lib"],
    java_name = "http_requests",
)

claro_library(
    name = "http_requests_lib",
    src = "http_requests.claro",
)

claro_binary(
    name = "http_server",
    srcs = [":http_server_lib"],
    java_name = "http_server",
)

claro_library(
    name = "http_server_lib",
    src = "http_server.claro",
)

claro_binary(
    name = "immutability_vs_mutability",
    srcs = [":immutability_vs_mutability_lib"],
    java_name = "immutability_vs_mutability",
)

claro_library(
    name = "immutability_vs_mutability_lib",
    src = "immutability_vs_mutability.claro",
)

claro_binary(
    name = "json_parsing",
    srcs = [":json_parsing_lib"],
    java_name = "json_parsing",
)

claro_library(
    name = "json_parsing_lib",
    src = "json_parsing.claro",
)

claro_binary(
    name = "keyword_generics_compiled_claro_image",
    srcs = [":keyword_generics_compiled_claro"],
    java_name = "keyword_generics",
)

claro_library(
    name = "keyword_generics_compiled_claro",
    src = "keyword_generics.claro",
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
    name = "maps_compiled_claro_image",
    srcs = [":maps_compiled_claro"],
    java_name = "maps",
)

claro_library(
    name = "maps_compiled_claro",
    src = "maps.claro",
)

claro_binary(
    name = "match",
    srcs = [":match_lib"],
    java_name = "match",
)

claro_library(
    name = "match_lib",
    src = "match.claro",
    debug = True
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
    name = "oneof_compiled_claro_image",
    srcs = [":oneof_compiled_claro"],
    java_name = "oneof",
)

claro_library(
    name = "oneof_compiled_claro",
    src = "oneof.claro",
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
    name = "recursive_aliases_compiled_claro_image",
    srcs = [":recursive_aliases_compiled_claro"],
    java_name = "recursive_aliases",
)

claro_library(
    name = "recursive_aliases_compiled_claro",
    src = "recursive_aliases.claro",
)

claro_binary(
    name = "repeat",
    srcs = [":repeat_lib"],
    java_name = "repeat",
)

claro_library(
    name = "repeat_lib",
    src = "repeat.claro",
)

claro_binary(
    name = "sets_compiled_claro_image",
    srcs = [":sets_compiled_claro"],
    java_name = "sets",
)

claro_library(
    name = "sets_compiled_claro",
    src = "sets.claro",
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
    name = "user_defined_types",
    srcs = [":user_defined_types_lib"],
    java_name = "user_defined_types",
)

claro_library(
    name = "user_defined_types_lib",
    src = "user_defined_types.claro",
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
