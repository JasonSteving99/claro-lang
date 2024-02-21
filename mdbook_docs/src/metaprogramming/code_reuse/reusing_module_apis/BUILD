load("//:rules.bzl", "claro_module", "claro_binary")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "reusing_module_apis",
    doc_template = "reusing_module_apis.tmpl.md",
    examples = [
        {
            "example": "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/animals_unusable:animal.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": ":unusable_BUILD_without_exports",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": ":BUILD_without_exports",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "animals_example_BUILD",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": "animals_example.claro",
            "deps": {
                "AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds",
                "Cat": "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/animals:Cat",
                "Dog": "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/animals:Dog",
            }
        },
        {
            "example": ":list_comp_BUILD_without_exports",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/macro_animals:animals.bzl",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": ":macro_BUILD_without_exports",
            "executable": False,
            "codeblock_css_class": "python",
        },
    ],
)

[
    genrule(
        name = name + "_rule",
        outs = [name],
        srcs = [build_file],
        cmd = "cat $(SRCS) | sed '/^exports_files/d; s/\\/\\/stdlib.*sounds/:animal_sounds/g; /visibility/d' > $(OUTS)",
    )
    for name, build_file in {
        "unusable_BUILD_without_exports": "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/animals_unusable:BUILD",
        "BUILD_without_exports": "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/animals:BUILD",
        "list_comp_BUILD_without_exports": "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/list_comprehension_animals:BUILD",
        "macro_BUILD_without_exports": "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/macro_animals:BUILD",
    }.items()
]