load("//:rules.bzl", "claro_module")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "initializers_and_unwrappers",
    doc_template = "initializers_and_unwrappers.tmpl.md",
    examples = [
        {
            "example": "ex1-no-init.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "ex1-bad-init-BUILD",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": "ex1-bad-init.claro",
            "deps": {
                "Nums": ":ex1_no_initializer",
            }
        },
        {
            "example": "ex1-with-init.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "ex1-with-init-BUILD",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": "ex1-bad-init.claro",
            "deps": {
                "Nums": ":ex1_with_initializer",
            },
            "expect_errors": True,
        },
        {
            "example": "ex1-good-init.claro",
            "deps": {
                "Nums": ":ex1_with_initializer",
            }
        },
    ],
)

claro_module(
    name = "ex1_no_initializer",
    module_api_file = "ex1-part1.claro_module_api",
)

claro_module(
    name = "ex1_with_initializer",
    module_api_file = "ex1-with-init.claro_module_api",
    srcs = ["ex1.claro"],
)

genrule(
    name = "concat_ex1_api",
    outs = ["ex1-with-init.claro_module_api"],
    srcs = ["ex1-part1.claro_module_api", "ex1-part2.claro_module_api"],
    cmd = '''
        echo '# ex1-with-init.claro_module_api' > $(OUTS) \
        && cat $(location ex1-part1.claro_module_api) $(location ex1-part2.claro_module_api) >> $(OUTS)
        '''
)

genrule(
    name = "concat_ex1_api_no_init",
    outs = ["ex1-no-init.claro_module_api"],
    srcs = ["ex1-part1.claro_module_api"],
    cmd = '''
        echo '# ex1-no-init.claro_module_api' > $(OUTS) \
        && cat $(location ex1-part1.claro_module_api) >> $(OUTS)
        '''
)
