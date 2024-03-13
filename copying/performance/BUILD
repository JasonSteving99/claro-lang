load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "performance",
    doc_template = "performance.tmpl.md",
    examples = [
        "ex1.claro",
        {
            "example": "ex2.claro",
            "deps": {
                "duration": "@claro-lang//stdlib/time/duration:duration",
                "instant": "@claro-lang//stdlib/time/instant:instant",
            },
        },
    ],
)
