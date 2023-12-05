load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "generic_return_type_inference",
    doc_template = "generic_return_type_inference.tmpl.md",
    examples = [
        {
            "example": "//mdbook_docs/src/generics/generic_return_type_inference:ex1.claro",
            "append_output": False,
        },
        {
            "example": "//mdbook_docs/src/generics/generic_return_type_inference:ex2.claro",
            "hidden_setup": "//mdbook_docs/src/generics/generic_return_type_inference:ex1.claro",
        },
    ],
)