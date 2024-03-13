load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "concrete_type_inference",
    doc_template = "concrete_type_inference.tmpl.md",
    examples = [
        "ex1_concat.claro",
        {
            "example": "ex2.claro",
            "hidden_setup": "ex-setup.claro",
            "expect_errors": True,
        },
        {
            "example": "ex3.claro",
            "hidden_setup": "ex-setup.claro",
        },
    ]
)

genrule(
    name = "ex1_concat",
    srcs = ["ex-setup.claro", "ex1.claro"],
    outs = ["ex1_concat.claro"],
    cmd = "cat $(location ex-setup.claro) $(location ex1.claro) > $(OUTS)"
)