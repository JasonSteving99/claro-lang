load("//:rules.bzl", "claro_module")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "opaque_types",
    doc_template = "opaque_types.tmpl.md",
    examples = [
        {
            "example": "ex1.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "ex1-impl.claro",
            "append_output": False,
        },
        {
            "example": "ex1-alt-impl.claro",
            "deps": {"ID": ":uuid"},
            "append_output": False,
        },
    ],
)

genrule(
    name = "uuid_gen",
    outs = ["uuid.claro_module_api"],
    cmd = "echo 'atom UUID' > $(OUTS)"
)

claro_module(name = "uuid", module_api_file = "uuid_gen")