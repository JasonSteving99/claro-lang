load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "static_typing",
    doc_template = "static_typing.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "expect_errors": True,
            "hidden_cleanup": "ex1-cleanup.claro",
        },
    ],
)
