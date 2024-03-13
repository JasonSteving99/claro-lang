load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "exhaustiveness_checks",
    doc_template = "exhaustiveness_checks.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "expect_errors": True,
        },
        "ex2.claro",
        {
            "example": "ex3.claro",
            "expect_errors": True,
        },
        "ex4.claro",
    ],
)
