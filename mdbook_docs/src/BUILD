load("@aspect_bazel_lib//lib:write_source_files.bzl", "write_source_file")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")
load("//stdlib/utils/expand_template:expand_template.bzl", "expand_template")

write_source_file(
    name = "write_all_docs",
    additional_update_targets = [
        ":generics",
        ":write_summary_md",
        "//mdbook_docs/src/chapter_1:hello_world",
        "//mdbook_docs/src/common_programming_concepts/variables_and_types:variables_and_types",
        "//mdbook_docs/src/common_programming_concepts/sep_var_decl_and_init:sep_var_decl_and_init",
        "//mdbook_docs/src/common_programming_concepts/string_formatting:string_formatting",
        "//mdbook_docs/src/common_programming_concepts/var_reassignment:var_reassignment",
        "//mdbook_docs/src/control_flow/collection_comprehension:collection_comprehension",
        "//mdbook_docs/src/control_flow/collection_comprehension/more_than_syntax_sugar:more_than_syntax_sugar",
        "//mdbook_docs/src/control_flow/if_else:if_else",
        "//mdbook_docs/src/control_flow/for:for",
        "//mdbook_docs/src/control_flow/match:match",
        "//mdbook_docs/src/control_flow/match/exhaustiveness_checks:exhaustiveness_checks",
        "//mdbook_docs/src/control_flow/match/structed_data:structured_data",
        "//mdbook_docs/src/control_flow/match/wildcards:wildcards",
        "//mdbook_docs/src/control_flow/pipes:pipes",
        "//mdbook_docs/src/control_flow/repeat:repeat",
        "//mdbook_docs/src/control_flow/while:while",
        "//mdbook_docs/src/copying:copying",
        "//mdbook_docs/src/copying/known_bugs:known_bugs",
        "//mdbook_docs/src/copying/mutability_coercion:mutability_coercion",
        "//mdbook_docs/src/copying/performance:performance",
        "//mdbook_docs/src/error_handling:error_handling",
        "//mdbook_docs/src/error_handling/error_propagation:error_propagation",
        "//mdbook_docs/src/generics/contracts:contracts",
        "//mdbook_docs/src/generics/contracts/dynamic_dispatch:dynamic_dispatch",
        "//mdbook_docs/src/generics/contracts/implementing_contracts:implementing_contracts",
        "//mdbook_docs/src/generics/contracts/multiple_type_params:multiple_type_params",
        "//mdbook_docs/src/generics/contracts/multiple_type_params/implied_types:implied_types",
        "//mdbook_docs/src/generics/generic_return_type_inference:generic_return_type_inference",
        "//mdbook_docs/src/getting_started/first_program:first_program",
        "//mdbook_docs/src/getting_started/intro_to_modules:intro_to_modules",
        "//mdbook_docs/src/getting_started/understanding_starter_project:understanding_starter_project",
        "//mdbook_docs/src/graph_procedures:graph_procedures",
        "//mdbook_docs/src/graph_procedures/calling_graph_procedures:calling_graph_procedures",
        "//mdbook_docs/src/graph_procedures/conditional_subgraph_execution:conditional_subgraph_execution",
        "//mdbook_docs/src/graph_procedures/graph_procedure_composition:graph_procedure_composition",
        "//mdbook_docs/src/guaranteed_data_race_free:guaranteed_data_race_free",
        "//mdbook_docs/src/guaranteed_deadlock_free:re_what_color_is_your_function",
        "//mdbook_docs/src/guaranteed_deadlock_free/blocking_procedures:blocking_procedures",
        "//mdbook_docs/src/guaranteed_deadlock_free/re_what_color_is_your_function:blocking_generics",
        "//mdbook_docs/src/images:logo",
        "//mdbook_docs/src/lambdas_and_first_class_procedures:lambdas_and_first_class_procedures",
        "//mdbook_docs/src/lambdas_and_first_class_procedures/lambda_closures:lambda_closures",
        "//mdbook_docs/src/module_system:module_system",
        "//mdbook_docs/src/module_system/module_apis:module_apis",
        "//mdbook_docs/src/module_system/module_apis/flags:flags",
        "//mdbook_docs/src/module_system/module_apis/http_services:http_services",
        "//mdbook_docs/src/module_system/module_apis/procedure_signatures:procedure_signatures",
        "//mdbook_docs/src/module_system/module_apis/static_values:static_values",
        "//mdbook_docs/src/module_system/module_apis/type_definitions:type_definitions",
        "//mdbook_docs/src/module_system/module_apis/type_definitions/initializers_and_unwrappers:initializers_and_unwrappers",
        "//mdbook_docs/src/module_system/module_apis/type_definitions/initializers_and_unwrappers/unwrappers:unwrappers",
        "//mdbook_docs/src/module_system/module_apis/type_definitions/opaque_types:opaque_types",
        "//mdbook_docs/src/procedures/consumers:consumers",
        "//mdbook_docs/src/procedures/functions:functions",
        "//mdbook_docs/src/procedures/providers:providers",
        "//mdbook_docs/src/resource_files:resource_files",
        "//mdbook_docs/src/static_typing:static_typing",
        "//mdbook_docs/src/static_typing/aliases:aliases",
        "//mdbook_docs/src/static_typing/aliases/not_a_new_type_decl:not_a_new_type_decl",
        "//mdbook_docs/src/static_typing/atoms:atoms",
        "//mdbook_docs/src/static_typing/builtin_colls:builtin_collections",
        "//mdbook_docs/src/static_typing/builtin_colls/list_type:list_type",
        "//mdbook_docs/src/static_typing/builtin_colls/map_type:map_type",
        "//mdbook_docs/src/static_typing/builtin_colls/set_type:set_type",
        "//mdbook_docs/src/static_typing/builtin_colls/struct_type:struct_type",
        "//mdbook_docs/src/static_typing/builtin_colls/tuple_type:tuple_type",
        "//mdbook_docs/src/static_typing/oneofs:oneofs",
        "//mdbook_docs/src/static_typing/oneofs/narrowing:narrowing",
        "//mdbook_docs/src/static_typing/user_defined_types:user_defined_types",
        "//mdbook_docs/src/static_typing/user_defined_types/recursive_types:recursive_types",
        "//mdbook_docs/src/static_typing/user_defined_types/parameterized_types:parameterized_types",
        "//mdbook_docs/src/static_typing/user_defined_types/parameterized_types/concrete_type_inference:concrete_type_inference",
        "//mdbook_docs/src/static_typing/user_defined_types/recursive_types/impossible_recursive_types:impossible_recursive_types",
        "//mdbook_docs/src/stdlib:write_all_stdlib_docs",
        "//mdbook_docs/src/stdlib/default_modules:default_modules",
        "//mdbook_docs/src/type_inference:type_inference",
        "//mdbook_docs/src/type_inference/required_type_annotations:required_type_annotations",
        "//mdbook_docs/theme:logo",
    ],
)


#################################################################
# BEGIN: Write Generated MdBook Table of Contents (SUMMARY.md). #
#################################################################
expand_template(
    name = "generate_summary_md",
    template = "SUMMARY.tmpl.md",
    substitutions = {
        "stdlib_toc": "//mdbook_docs/src/stdlib:mdbook_stdlib_toc",
    },
    out = "SUMMARY.templatted.md".format(module_name),
)
write_source_file(
    name = "write_summary_md",
    in_file = "generate_summary_md",
    out_file = "SUMMARY.md",
    suggested_update_target = "//mdbook_docs/src:write_all_docs",
)
#################################################################
# END: Write Generated MdBook Table of Contents (SUMMARY.md).   #
#################################################################


doc_with_validated_examples(
    name = "generics",
    doc_template = "generics.tmpl.md",
    examples = [
        {
            "example": "//mdbook_docs/src/generics:ex_reduce.claro",
            "append_output": False,
        },
        {
            "example": "//mdbook_docs/src/generics:ex2.claro",
            "hidden_setup": "//mdbook_docs/src/generics:ex_reduce.claro",
        }
    ],
)