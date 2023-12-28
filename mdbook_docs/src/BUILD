load("@aspect_bazel_lib//lib:write_source_files.bzl", "write_source_file")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

write_source_file(
    name = "write_all_docs",
    additional_update_targets = [
        ":generics",
        "//mdbook_docs/src/chapter_1:hello_world",
        "//mdbook_docs/src/generics/contracts:contracts",
        "//mdbook_docs/src/generics/contracts/implementing_contracts:implementing_contracts",
        "//mdbook_docs/src/generics/generic_return_type_inference:generic_return_type_inference",
        "//mdbook_docs/src/static_typing/builtin_colls/list_type:list_type",
        "//mdbook_docs/src/static_typing/builtin_colls/map_type:map_type",
        "//mdbook_docs/src/static_typing/builtin_colls/set_type:set_type",
        "//mdbook_docs/src/static_typing/builtin_colls/tuple_type:tuple_type",
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