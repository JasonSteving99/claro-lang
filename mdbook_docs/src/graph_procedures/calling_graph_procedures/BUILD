load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "calling_graph_procedures",
    doc_template = "calling_graph_procedures.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "hidden_setup": [
                "//mdbook_docs/src/graph_procedures:ex1.claro",
                "//mdbook_docs/src/graph_procedures:ex1-setup.claro",
            ],
            "hidden_cleanup": "ex1-cleanup.claro",
            "append_output": False,
        },
        {
            "example": "ex2.claro",
            "hidden_setup": [
                "//mdbook_docs/src/graph_procedures:ex1.claro",
                "//mdbook_docs/src/graph_procedures:ex1-setup.claro",
            ],
        },
        {
            "example": "ex3.claro.txt",
            "executable": False,
            "codeblock_css_class": "claro",
        },
    ],
)
