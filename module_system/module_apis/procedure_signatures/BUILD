load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "procedure_signatures",
    doc_template = "procedure_signatures.tmpl.md",
    examples = [
        {
            "example": "strings_signatures.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        }
    ],
)

genrule(
    name = "signatures_from_strings_module",
    outs = ["strings_signatures.claro_module_api"],
    srcs = ["//stdlib/strings:strings.claro_module_api"],
    cmd = '''
        echo -n '# strings.claro_module_api\n\n' > $(OUTS) \
        && cat $(location //stdlib/strings:strings.claro_module_api) | grep -e fromChar -e charAt >> $(OUTS) \
        && echo -n '\n# ...' >> $(OUTS)
        '''
)