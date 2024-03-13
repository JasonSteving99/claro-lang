load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "multiple_type_params",
    doc_template = "multiple_type_params.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "append_output": False,
        },
        {
            "example": "ex2.claro",
            "hidden_setup": "ex1.claro",
            "append_output": False,
        },
        {
            "example": "ex3.claro",
            "hidden_setup": "ex1.claro",
        },
    ],
)
