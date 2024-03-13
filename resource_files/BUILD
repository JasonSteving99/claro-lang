load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "resource_files",
    doc_template = "resource_files.tmpl.md",
    examples = [
        {
            "example": "ex_BUILD",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": "ex1.claro",
            "resources": {"MyResource": "example_resource.txt"},
        }
    ],
)
