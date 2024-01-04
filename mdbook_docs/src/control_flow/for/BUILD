load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "for",
    doc_template = "for.tmpl.md",
    examples = [
        "ex1.claro",
        "ex2.claro",
        "ex3.claro",
        {
            "example": "ex4.claro",
            "expect_errors": True,
        },
        "ex5.claro",
        "ex6.claro",
    ],
)
