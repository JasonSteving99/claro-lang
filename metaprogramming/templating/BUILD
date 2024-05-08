load("//stdlib/utils/expand_template:expand_template.bzl", "expand_template")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

expand_template(
    name = "templating_tmpl_md",
    template = "templating.tmpl.md.tmpl",
    out = "templating.tmpl.md",
    substitutions = {
        "HELLO_WORLD_PAGE_MD_EXAMPLE": "//mdbook_docs/src/chapter_1:expand_hello_world",
    },
    visibility = ["//mdbook_docs/src/metaprogramming/templating:__pkg__"],
)

doc_with_validated_examples(
    name = "templating",
    doc_template = "templating.tmpl.md",
    examples = [
        {
            "example": "//mdbook_docs/src/metaprogramming/templating/examples:ex1.claro.tmpl",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "examples_BUILD_without_exports",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": "ex1.claro",
            "append_output": False,
        },
        {
            "example": "//mdbook_docs/src/chapter_1:hello_world.tmpl.md",
            "executable": False,
            "codeblock_css_class": "md",
        },
        {
            "example": "hello_world_BUILD_without_exports",
            "executable": False,
            "codeblock_css_class": "python",
        },
    ],
)

genrule(
    name = "examples_BUILD_without_exports",
    outs = ["examples_BUILD"],
    srcs = ["//mdbook_docs/src/metaprogramming/templating/examples:BUILD"],
    cmd = "cat $(SRCS) | sed '/^exports_files/d; /visibility/d' > $(OUTS)",
)

genrule(
    name = "ex1_renamed",
    outs = ["ex1.claro"],
    srcs = ["//mdbook_docs/src/metaprogramming/templating/examples:ex1.claro"],
    cmd = "cat $(SRCS) | sed 's/\\.tmpl//g' > $(OUTS)",
)

genrule(
    name = "hello_world_BUILD_without_exports",
    outs = ["hello_world_BUILD"],
    srcs = ["//mdbook_docs/src/chapter_1:BUILD"],
    cmd = "cat $(SRCS) | sed '/^exports_files/d' > $(OUTS)",
)
