load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "type_definitions",
    doc_template = "type_definitions.tmpl.md",
    examples = [
        {
            "example": "std_signatures.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "strings_signatures.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "ex3.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "ex4.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "ex5_no_srcs_BUILD",
            "executable": False,
            "codeblock_css_class": "claro",
        },
    ],
)

[
    genrule(
        name = "signatures_from_{0}_module".format(args["module_name"]),
        outs = ["{0}_signatures.claro_module_api".format(args["module_name"])],
        srcs = [args["src"]],
        cmd = '''
            echo -n '# {module_name}.claro_module_api\n\n' > $(OUTS) \
            && cat $(location {src}) | grep {grep_args} >> $(OUTS) \
            && echo -n '\n# ...' >> $(OUTS)
            '''.format(
                module_name = args["module_name"],
                src = args["src"],
                grep_args = args["grep_args"]
            )
    )
    for args in [
        {
            "module_name": "std",
            "grep_args": "-e 'newtype Error'",
            "src": "//stdlib:std.claro_module_api",
        },
        {
            "module_name": "strings",
            "grep_args": "-e NOT_FOUND",
            "src": "//stdlib/strings:strings.claro_module_api",
        },
    ]
]
