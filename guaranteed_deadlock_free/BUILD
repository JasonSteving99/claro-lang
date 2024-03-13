load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

exports_files(glob(["ex1*.claro"]), visibility=["//mdbook_docs/src/graph_procedures/calling_graph_procedures:__pkg__"])

doc_with_validated_examples(
    name = "re_what_color_is_your_function",
    doc_template = "re_what_color_is_your_function.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "ex2.claro",
            "executable": False,
            "codeblock_css_class": "claro",
        },
    ],
)
