load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "module_system",
    doc_template = "module_system.tmpl.md",
    examples = [
        {
            "example": "//mdbook_docs/src/module_system/examples:example.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "//mdbook_docs/src/module_system/examples:contract_impl.claro",
            "hidden_setup": "//mdbook_docs/src/module_system/examples:example_api_defs.claro_module_api",
            "append_output": False,
        },
        {
            "example": "//mdbook_docs/src/module_system/examples:pretty_print.claro",
            "hidden_setup": [
                "//mdbook_docs/src/module_system/examples:example_api_defs.claro_module_api",
                "//mdbook_docs/src/module_system/examples:contract_impl.claro",
            ],
            "append_output": False,
            "deps": {
                "Boxes": "//mdbook_docs/src/module_system/examples:box",
            }
        },
        {
            "example": "demo_module_BUILD",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": "demo_binary_w_dep_on_demo_module_BUILD",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": "//mdbook_docs/src/module_system/examples:test.claro",
            "deps": {
                "Ex": "//mdbook_docs/src/module_system/examples:example",
            }
        },
    ],
)
