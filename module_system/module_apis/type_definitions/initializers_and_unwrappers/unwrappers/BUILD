load("//:rules.bzl", "claro_module")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "unwrappers",
    doc_template = "unwrappers.tmpl.md",
    examples = [
        {
            "example": "ex1-api.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "violate-mut-type-constraints.claro",
            "deps": {
                "Person": ":person",
            }
        },
        {
            "example": "ex1-api-with-unwrappers.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "violate-mut-type-constraints.claro",
            "deps": {
                "Person": ":person_with_unwrappers",
            },
            "expect_errors": True,
        },
        {
            "example": "violate-mut-type-constraints-using-setAge.claro",
            "deps": {
                "Person": ":person_with_unwrappers",
            },
        },
    ],
)

claro_module(
    name = "person",
    module_api_file = "ex1-api.claro_module_api",
    srcs = ["ex1-impl.claro"],
)

claro_module(
    name = "person_with_unwrappers",
    module_api_file = "ex1-api-with-unwrappers.claro_module_api",
    srcs = [
        "ex1-impl.claro",
        "ex1-unwrappers-impl.claro",
    ],
)

genrule(
    name = "person-with-unwrappers-api",
    outs = ["ex1-api-with-unwrappers.claro_module_api"],
    srcs = ["ex1-api.claro_module_api", "ex1-api-unwrappers.claro_module_api"],
    cmd = "cat $(location ex1-api.claro_module_api) $(location ex1-api-unwrappers.claro_module_api) > $(OUTS)"
)
