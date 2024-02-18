load("//:rules.bzl", "claro_module", "claro_binary")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "json_parsing",
    doc_template = "json_parsing.tmpl.md",
    examples = [
        {
            "example": "//mdbook_docs/src/module_system/module_apis/static_values:ex-server-config.json",
            "executable": False,
            "codeblock_css_class": "json",
        },
        {
            "example": "ex1-format.claro",
            "append_output": False,
        },
        {
            "example": "ex1.claro",
            "hidden_setup": "ex1-format.claro",
            "resources": {
                "ServerConfigJSON": "//mdbook_docs/src/module_system/module_apis/static_values:ex-server-config.json"
            },
        },
    ],
)
