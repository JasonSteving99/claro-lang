load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "aliases",
    doc_template = "aliases.tmpl.md",
    examples = [
        "ex1.claro",
        "ex2.claro",
        {
            "example": "ex3.claro",
            "hidden_setup": "ex2.claro",
        }
    ],
)
