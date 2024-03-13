load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "recursive_types",
    doc_template = "recursive_types.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "append_output": False,
        },
        {
            "example": "ex2.claro",
            "hidden_setup": "ex1.claro",
        },
        {
            "example": "ex3.claro",
            "append_output": False,
        },
        {
            "example": "ex4.claro",
            "hidden_setup": "ex3.claro",
        },
    ]
)
