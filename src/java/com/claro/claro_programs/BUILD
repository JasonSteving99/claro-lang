package(default_visibility = ["//visibility:public"])

load("//src/java/com/claro:claro_build_rules.bzl", "claro_binary", "gen_claro_builtin_java_deps_jar")

# Generate target for the builtin Java deps that'll be needed by the CLI to build w/o Bazel.
gen_claro_builtin_java_deps_jar()

claro_binary(
    name = "atoms",
    main_file = "atoms.claro",
)

claro_binary(
    name = "collection_comprehensions",
    main_file = "collection_comprehensions.claro",
)

claro_binary(
    name = "contracts",
    main_file = "contracts.claro",
)

claro_binary(
    name = "contracts_implied_types",
    main_file = "contracts_implied_types.claro",
)

claro_binary(
    name = "copy",
    main_file = "copy.claro",
)

claro_binary(
    name = "dynamic_dispatch",
    main_file = "dynamic_dispatch.claro",
)

claro_binary(
    name = "error_handling_and_propagation",
    main_file = "error_handling_and_propagation.claro",
)

claro_binary(
    name = "generics",
    main_file = "generics.claro",
)

claro_binary(
    name = "generic_return_inference_experimentation",
    main_file = "generic_return_inference_experimentation.claro",
)

claro_binary(
    name = "for_loops",
    main_file = "for_loops.claro",
)

claro_binary(
    name = "format_strings",
    main_file = "format_strings.claro",
)

claro_binary(
    name = "graphs",
    main_file = "graphs.claro",
)

claro_binary(
    name = "http_requests",
    main_file = "http_requests.claro",
    optional_stdlib_deps = ["http"],
)

claro_binary(
    name = "immutability",
    main_file = "immutability_vs_mutability.claro",
)

claro_binary(
    name = "json",
    main_file = "json_parsing.claro",
)

claro_binary(
    name = "keyword",
    main_file = "keyword_generics.claro",
)

claro_binary(
    name = "lambdas",
    main_file = "lambdas.claro",
)

claro_binary(
    name = "list",
    main_file = "list.claro",
)

claro_binary(
    name = "maps",
    main_file = "maps.claro",
)

claro_binary(
    name = "match",
    main_file = "match.claro",
)

claro_binary(
    name = "modules",
    main_file = "modules.claro",
)

claro_binary(
    name = "oneof",
    main_file = "oneof.claro",
)

claro_binary(
    name = "piping",
    main_file = "piping.claro",
)

claro_binary(
    name = "procedures",
    main_file = "procedures.claro",
)

claro_binary(
    name = "recursion",
    main_file = "recursion.claro",
)

# TODO(steving) TESTING!!! DO NOT SUBMIT! THIS IS NOT ACTUALLY WORKING. GOT REVEALED ONCE APPEND WAS MADE INTO A PROPER
# TODO(steving) TESTING!!!   FUNCTION INSTEAD OF A COMPILER BUILTIN. NEED TO DEPRECATE SUPPORT FOR RECURSIVE ALIASES!
claro_binary(
    name = "recursive",
    main_file = "recursive_aliases.claro",
)

claro_binary(
    name = "repeat",
    main_file = "repeat.claro",
)

claro_binary(
    name = "sets",
    main_file = "sets.claro",
)

claro_binary(
    name = "structs",
    main_file = "structs.claro",
)

claro_binary(
    name = "tuples",
    main_file = "tuples.claro",
)

claro_binary(
    name = "types",
    main_file = "types.claro",
)

claro_binary(
    name = "user_defined_types",
    main_file = "user_defined_types.claro",
)

claro_binary(
    name = "while",
    main_file = "while_test.claro",
)
