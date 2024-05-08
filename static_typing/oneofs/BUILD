load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "oneofs",
    doc_template = "oneofs.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "hidden_cleanup": "ex1-cleanup.claro",
            "append_output": False,
        },
        "ex2.claro",
        {
            "example": "ex3.claro",
            "expect_errors": True,
        },
    ]
)
