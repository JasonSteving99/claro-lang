load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

exports_files(glob(["*.claro"]), visibility = ["//mdbook_docs/src/generics/contracts/implementing_contracts:__pkg__"])

doc_with_validated_examples(
    name = "dynamic_dispatch",
    doc_template = "dynamic_dispatch.tmpl.md",
    examples = [
        "ex1_concat.claro",
        {
            "example": "ex2.java",
            "executable": False,
            "codeblock_css_class": "java",
        },
        {
            "example": "ex3.java",
            "executable": False,
            "codeblock_css_class": "java",
        },
        "ex4_concat.claro",
        {
            "example": "ex5.java",
            "executable": False,
            "codeblock_css_class": "java",
        },
        {
            "example": "ex6.claro",
            "hidden_setup": "claro_contracts_setup.claro",
            "append_output": False,
        },
        {
            "example": "ex7.claro",
            "hidden_setup": ["claro_contracts_setup.claro", "ex6.claro"],
            "expect_errors": True,
        },
        {
            "example": "ex8.claro",
            "hidden_setup": ["claro_contracts_setup.claro", "ex6.claro"],
        },
        { # TODO(steving) This example is broken but should actually work.
            "example": "ex9.claro",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "ex10.claro",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "ex11.claro",
            "executable": False,
            "codeblock_css_class": "claro",
        },
    ],
)

[
    genrule(
        name = "{0}_concat".format(ex),
        outs = ["{0}_concat.claro".format(ex)],
        srcs = ["claro_contracts_setup.claro", "{0}.claro".format(ex)],
        cmd = "cat $(location {0}.claro) $(location claro_contracts_setup.claro) > $(OUTS)".format(ex),
    )
    for ex in ["ex1", "ex4"]
]