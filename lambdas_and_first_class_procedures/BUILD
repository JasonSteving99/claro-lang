load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "lambdas_and_first_class_procedures",
    doc_template = "lambdas_and_first_class_procedures.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "append_output": False,
            "hidden_cleanup": "ex1-cleanup.claro",
        },
        {
            "example": "//mdbook_docs/src/type_inference/required_type_annotations:ex3.claro",
            "append_output": False,
            "hidden_cleanup": "//mdbook_docs/src/type_inference/required_type_annotations:ex2-cleanup.claro",
        },
        "ex2.claro",
    ],
)
