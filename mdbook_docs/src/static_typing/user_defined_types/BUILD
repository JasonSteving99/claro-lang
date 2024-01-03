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
            "hidden_setup": "ex1.claro",
        },
        {
            "example": "ex3.claro",
            "expect_errors": True,
        },
        "ex4.claro",
        {
            "example": "ex5-bad-concat.claro",
            "expect_errors": True,
        },
        {
            "example": "ex5-good.claro",
            "hidden_setup": "ex5.claro",
        },
    ]
)

genrule(
    name = "ex5-bad-concat",
    srcs = ["ex5.claro", "ex5-bad.claro"],
    outs = ["ex5-bad-concat.claro"],
    cmd = "cat $(location ex5.claro) $(location ex5-bad.claro) > $(OUTS)"
)