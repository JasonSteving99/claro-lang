load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "lambda_closures",
    doc_template = "lambda_closures.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "expect_errors": True,
        },
        "ex2.claro",
        "ex3.claro",
    ],
)
