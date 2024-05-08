load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

exports_files(glob(["ex1*.claro"]), visibility=["//mdbook_docs/src/graph_procedures/calling_graph_procedures:__pkg__"])

doc_with_validated_examples(
    name = "blocking_generics",
    doc_template = "blocking_generics.tmpl.md",
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
            "expect_errors": True,
        },
        {
            "example": "ex4.claro",
            "expect_errors": True,
        },
        {
            "example": "ex5.claro",
            "hidden_setup": "ex1.claro",
            "hidden_cleanup": "ex5-cleanup.claro",
            "append_output": False,
        },
    ],
)
