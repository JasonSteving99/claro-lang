load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

exports_files(glob(["ex1*.claro"]), visibility=["//mdbook_docs/src/graph_procedures/calling_graph_procedures:__pkg__"])

doc_with_validated_examples(
    name = "blocking_procedures",
    doc_template = "blocking_procedures.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "hidden_setup": "ex1-setup.claro",
            "append_output": False,
        },
        {
            "example": "ex2.claro",
            "hidden_setup": ["ex2-setup.claro", "ex1-setup.claro"],
            "expect_errors": True,
        },
    ],
)
