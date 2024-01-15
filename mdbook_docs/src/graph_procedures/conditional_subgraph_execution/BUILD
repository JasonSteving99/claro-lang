load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

exports_files(glob(["ex1*.claro"]), visibility=["//mdbook_docs/src/graph_procedures/calling_graph_procedures:__pkg__"])

doc_with_validated_examples(
    name = "conditional_subgraph_execution",
    doc_template = "conditional_subgraph_execution.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "hidden_setup": "ex1-setup.claro",
            "append_output": False,
        },
    ],
)
