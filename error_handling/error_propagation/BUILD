load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "error_propagation",
    doc_template = "error_propagation.tmpl.md",
    examples = [
        "ex1.claro",
        {
            "example": "ex2.claro",
            "append_output": False,
        }
    ],
)
