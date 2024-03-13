load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "guaranteed_data_race_free",
    doc_template = "guaranteed_data_race_free.tmpl.md",
    examples = [
        {
            "example": "ex1.claro",
            "expect_errors": True,
            "hidden_setup": "ex1-setup.claro",
        },
        {
            "example": "ex2.claro",
            "expect_errors": True,
            "hidden_setup": "ex2-setup.claro",
        },
        {
            "example": "ex3.claro",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "ex4.claro",
            "executable": False,
            "codeblock_css_class": "claro",
        },
    ],
)
