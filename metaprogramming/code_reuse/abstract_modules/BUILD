load(":animal.bzl", "Animal")
load("//:rules.bzl", "claro_module", "claro_binary")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "abstract_modules",
    doc_template = "abstract_modules.tmpl.md",
    examples = [
        {
            "example": "macro_def.bzl",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": "dog_and_cat_modules_BUILD",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": "platypus_animal_sounds_impl.claro",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "platypus_module_BUILD",
            "executable": False,
            "codeblock_css_class": "python",
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
                "Cat": ":cat",
                "Dog": ":dog",
                "Platypus": ":platypus",
            }
        },
    ],
)

[
    Animal(
        name = animal,
        api_extensions = [":{0}_cons.claro_module_api".format(animal)],
        override = {
            "InternalStateAndConstructor": ":{0}_state_and_cons.claro".format(animal),
            "MakeNoiseImpl": ":{0}_noise.claro".format(animal),
        },
    )
    for animal in ["dog", "cat"]
]
Animal(
    name = "platypus",
    api_extensions = [":platypus_cons.claro_module_api"],
    override = {
        "AnimalSoundsImpl": ":platypus_animal_sounds_impl.claro",
        "InternalStateAndConstructor": ":platypus_state_and_cons.claro",
    },
)

claro_binary(
    name = "test",
    main_file = "animals_example.claro",
    deps = {
        "AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds",
        "Cat": ":cat",
        "Dog": ":dog",
        "Platypus": ":platypus",
    }
)



genrule(
    name = "copy",
    outs = ["animal.claro_module_api", "default_animal_sounds_impl.claro", "dog_cons.claro_module_api", "cat_cons.claro_module_api"],
    srcs = [
        "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/animals:cat_cons.claro_module_api",
        "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/animals:dog_cons.claro_module_api",
        "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis/macro_animals:animal.claro_module_api",
        "//mdbook_docs/src/metaprogramming/code_reuse/reusing_source_code:default_animal_sounds_impl.claro",
    ],
    cmd = "cp $(SRCS) $(RULEDIR)",
)
genrule(
    name = "platypus_cons",
    srcs = ["dog_cons.claro_module_api"],
    outs = ["platypus_cons.claro_module_api"],
    cmd = "cat $(SRCS) > $(OUTS)",
)
genrule(
    name = "macro_def_rule",
    outs = ["macro_def.bzl"],
    srcs = ["animal.bzl"],
    cmd = "cat $(SRCS) | sed 's/\\/\\/stdlib.*sounds/:animal_sounds/g' > $(OUTS)",
)