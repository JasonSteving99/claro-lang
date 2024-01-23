load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "implied_types",
    doc_template = "implied_types.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "append_output": False,
        },
        {
            "example": "ex2.claro",
            "hidden_setup": "ex1.claro",
            "append_output": False,
        },
        {
            "example": "ex3.claro",
            "hidden_setup": ["ex1.claro", "ex2.claro"],
            "hidden_cleanup": "ex3-cleanup.claro",
            "expect_errors": True,
        },
        {
            "example": "ex4.claro",
            "append_output": False,
        },
        {
            "example": "ex2.claro",
            "hidden_setup": "ex4.claro",
            "expect_errors": True,
        },
        {
            "example": "concat_ex6.claro",
            "hidden_setup": "ex4.claro",
        },
    ],
)

genrule(
    name = "concat_ex6",
    outs = ["concat_ex6.claro"],
    srcs = ["ex6.claro", "ex3.claro"],
    cmd = "cat $(location ex6.claro) $(location ex3.claro) > $(OUTS)",
)