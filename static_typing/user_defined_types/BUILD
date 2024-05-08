load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "user_defined_types",
    doc_template = "user_defined_types.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "append_output": False
        },
        {
            "example": "ex2.claro",
            "append_output": False
        },
        {
            "example": "ex3.claro",
            "hidden_setup": "ex1.claro",
        },
        {
            "example": "ex4.claro",
            "expect_errors": True,
        },
        "ex5.claro",
        {
            "example": "ex6-bad-concat.claro",
            "expect_errors": True,
        },
        {
            "example": "ex6-good.claro",
            "hidden_setup": "ex6.claro",
        },
    ]
)

genrule(
    name = "ex6-bad-concat",
    srcs = ["ex6.claro", "ex6-bad.claro"],
    outs = ["ex6-bad-concat.claro"],
    cmd = "cat $(location ex6.claro) $(location ex6-bad.claro) > $(OUTS)"
)