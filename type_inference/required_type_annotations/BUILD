load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

exports_files(
    ["ex3.claro", "ex2-cleanup.claro"],
    visibility = ["//mdbook_docs/src/lambdas_and_first_class_procedures:__pkg__"]
)

doc_with_validated_examples(
    name = "required_type_annotations",
    doc_template = "required_type_annotations.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "append_output": False,
        },
        {
            "example": "ex2.claro",
            "append_output": False,
            "hidden_cleanup": "ex2-cleanup.claro",
        },
        {
            "example": "ex3.claro",
            "append_output": False,
            "hidden_cleanup": "ex2-cleanup.claro",
        },
        {
            "example": "ex4.claro",
            "append_output": False,
            "hidden_cleanup": "ex4-cleanup.claro",
        },
        {
            "example": "ex5.claro",
            "append_output": False,
        },
        "ex6.claro",
        {
            "example": "ex7.claro",
            "append_output": False,
        },
        "ex8.claro",
    ],
)
