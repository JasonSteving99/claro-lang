load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "generic_return_type_inference",
    doc_template = "generic_return_type_inference.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "append_output": False,
        },
        {
            "example": "ex2.claro",
            "hidden_setup": "ex1.claro",
        },
        {
            "example": "ex3.claro",
            "hidden_setup": "ex1.claro",
            "hidden_cleanup": "ex3-cleanup.claro",
            "expect_errors": True,
        },
        {
            "example": "ex4.claro",
            "hidden_setup": "ex1.claro",
            "hidden_cleanup": "ex3-cleanup.claro",
            "expect_errors": True,
        },
        {
            "example": "ex5.claro",
            "hidden_setup": "ex1.claro",
        },
    ],
)