load("//:rules.bzl", "claro_module", "claro_binary")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")
load("//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/macro_animals:animals.bzl", "Animal")

doc_with_validated_examples(
    name = "reusing_source_code",
    doc_template = "reusing_source_code.tmpl.md",
    examples = [
        {
            "example": "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis:animals_example.claro",
            "deps": {
                "AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds",
                "Cat": "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/animals:cat",
                "Dog": "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/animals:dog",
            }
        },
        {
            "example": "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis:macro_BUILD_without_exports",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": "cat.claro",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": ":demo_output",
            "executable": False,
        },
        {
            "example": "get_message_with_name.claro",
            "hidden_setup": ["setup.claro", "cat-defer.claro"],
            "deps": {
                "AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds",
            },
            "append_output": False,
        },
        {
            "example": "example_shared_src_BUILD",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": "cat-defer.claro",
            "hidden_setup": ["setup.claro", "get_message_with_name.claro"],
            "deps": {
                "AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds",
            },
            "append_output": False,
        },
        {
            "example": "default_animal_sounds_impl.claro",
            "hidden_setup": ["setup.claro", "cat-inheritance.claro"],
            "deps": {
                "AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds",
            },
            "append_output": False,
        },
        {
            "example": "cat-inheritance.claro",
            "hidden_setup": ["setup.claro", "default_animal_sounds_impl.claro"],
            "deps": {
                "AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds",
            },
            "append_output": False,
        },
        {
            "example": "example_inheritance_BUILD",
            "executable": False,
            "codeblock_css_class": "python",
        },
    ],
)

Animal(name = "cat", srcs = ["cat-impl.claro"])
Animal(name = "dog", srcs = ["dog.claro"])

claro_binary(
    name = "demo",
    main_file = "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis:animals_example.claro",
    deps = {
        "AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds",
        "Cat": ":cat",
        "Dog": ":dog",
    }
)
genrule(
    name = "demo_output",
    outs = ["demo_output.txt"],
    srcs = [":demo_deploy.jar"],
    cmd = "$(JAVA) -jar $(SRCS) > $(OUTS)",
    tools = ["@bazel_tools//tools/jdk:current_java_runtime"],
    toolchains = ["@bazel_tools//tools/jdk:current_java_runtime"],
)

genrule(
    name = "copy",
    outs = ["{0}.claro_module_api".format(name) for name in ["animal", "cat_cons", "dog_cons"]],
    srcs = [
        "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/macro_animals:animal.claro_module_api",
        "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/macro_animals:cat_cons.claro_module_api",
        "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/macro_animals:dog_cons.claro_module_api",
    ],
    cmd = "cp $(SRCS) $(RULEDIR)",
)

genrule(
    name = "cat_impl",
    outs = ["cat-impl.claro"],
    srcs = ["cat.claro"],
    cmd = "cat $(SRCS) | sed 's/\\$$//g' > $(OUTS)",
)