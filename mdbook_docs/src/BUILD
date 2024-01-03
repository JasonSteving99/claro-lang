load("@aspect_bazel_lib//lib:write_source_files.bzl", "write_source_file")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

write_source_file(
    name = "write_all_docs",
    additional_update_targets = [
        ":generics",
        "//mdbook_docs/src/chapter_1:hello_world",
        "//mdbook_docs/src/common_programming_concepts/variables_and_types:variables_and_types",
        "//mdbook_docs/src/common_programming_concepts/sep_var_decl_and_init:sep_var_decl_and_init",
        "//mdbook_docs/src/common_programming_concepts/var_reassignment:var_reassignment",
        "//mdbook_docs/src/control_flow/if_else:if_else",
        "//mdbook_docs/src/control_flow/for:for",
        "//mdbook_docs/src/control_flow/pipes:pipes",
        "//mdbook_docs/src/control_flow/while:while",
        "//mdbook_docs/src/generics/contracts:contracts",
        "//mdbook_docs/src/generics/contracts/implementing_contracts:implementing_contracts",
        "//mdbook_docs/src/generics/generic_return_type_inference:generic_return_type_inference",
        "//mdbook_docs/src/static_typing/aliases:aliases",
        "//mdbook_docs/src/static_typing/aliases/not_a_new_type_decl:not_a_new_type_decl",
        "//mdbook_docs/src/static_typing/builtin_colls:builtin_collections",
        "//mdbook_docs/src/static_typing/builtin_colls/list_type:list_type",
        "//mdbook_docs/src/static_typing/builtin_colls/map_type:map_type",
        "//mdbook_docs/src/static_typing/builtin_colls/set_type:set_type",
        "//mdbook_docs/src/static_typing/builtin_colls/struct_type:struct_type",
        "//mdbook_docs/src/static_typing/builtin_colls/tuple_type:tuple_type",
        "//mdbook_docs/src/static_typing/user_defined_types:user_defined_types",
    ],
)


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