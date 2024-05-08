load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "narrowing",
    doc_template = "narrowing.tmpl.md",
    examples = [
        "ex1.claro",
        {
            "example": "ex2.claro",
            "expect_errors": True,
        },
        "ex3.claro",
    ]
)
