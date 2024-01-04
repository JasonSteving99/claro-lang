load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "match",
    doc_template = "match.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "append_output": False,
        },
        {
            "example": "ex2.claro",
            "append_output": False,
        },
        {
            "example": "ex3.claro",
            "append_output": False,
        },
        "ex4.claro",
        {
            "example": "ex5.claro",
            "expect_errors": True,
        },
    ],
)
