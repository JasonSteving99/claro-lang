load("//:rules.bzl", "claro_module", "claro_binary")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "http_services",
    doc_template = "http_services.tmpl.md",
    examples = [
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
    ],
)

claro_module(
    name = "buggy_buggies",
    module_api_file = "ex1.claro_module_api",
    srcs = ["ex1-impl.claro"],
    optional_stdlib_deps = ["http"],
)
claro_binary(
    name = "test",
    main_file = "ex2.claro",
    deps = {"BuggyBuggies": ":buggy_buggies"},
    optional_stdlib_deps = ["http"],
)