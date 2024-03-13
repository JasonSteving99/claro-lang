load("//:rules.bzl", "claro_module", "claro_binary")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "flags",
    doc_template = "flags.tmpl.md",
    examples = [
        {
            "example": "flag_only.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "flag_only_BUILD",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": "ex1-test.claro",
            "deps": {"Config": ":config"},
        },
        {
            "example": "ex1.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "ex1-impl.claro",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "ex1-test2.claro",
            "executable": False,
            "codeblock_css_class": "claro",
        },
    ],
)

claro_module(
    name = "config",
    module_api_file = "flag_only.claro_module_api",
)

claro_module(
    name = "config_with_subject",
    module_api_file = "ex1.claro_module_api",
    srcs = ["ex1-impl.claro"],
)

claro_binary(
    name = "demo",
    main_file = "ex1-test.claro",
    deps = {"Config": ":config"},
)

claro_binary(
    name = "demo2",
    main_file = "ex1-test2.claro",
    deps = {"Config": ":config_with_subject"},
)

genrule(
    name = "flag_only_api",
    outs = ["flag_only.claro_module_api"],
    srcs = ["ex1.claro_module_api"],
    cmd = '''
        echo -n '# ex1.claro_module_api\n\n' > $(OUTS) \
        && cat $(location ex1.claro_module_api) | grep -e env -e "command line" >> $(OUTS)
        '''
)
