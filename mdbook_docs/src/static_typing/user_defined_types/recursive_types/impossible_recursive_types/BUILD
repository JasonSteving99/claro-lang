load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "impossible_recursive_types",
    doc_template = "impossible_recursive_types.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "expect_errors": True,
        },
    ]
)
