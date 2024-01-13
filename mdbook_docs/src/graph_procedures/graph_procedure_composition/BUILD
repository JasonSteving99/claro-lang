load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "graph_procedure_composition",
    doc_template = "graph_procedure_composition.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "hidden_setup": "ex1-setup.claro",
            "append_output": False,
        },
        {
            "example": "ex2.claro",
            "hidden_setup": ["ex2-setup.claro", "ex1-setup.claro", "ex1.claro"],
            "append_output": False,
        },
    ],
)
