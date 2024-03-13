load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

exports_files(glob(["ex1*.claro"]), visibility=["//mdbook_docs/src/graph_procedures/calling_graph_procedures:__pkg__"])

doc_with_validated_examples(
    name = "graph_procedures",
    doc_template = "graph_procedures.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "hidden_setup": "ex1-setup.claro",
            "append_output": False,
        },
    ],
)
