load("//:rules.bzl", "claro_module")


claro_module(
    name = "Cat",
    module_api_file = "CatAnimal.claro_module_api",
    srcs = [
        "CatAnimal.claro",
        "//examples/claro_programs/module_system/emergent_inheritance_support/animals:Animal.claro",
    ],
    deps = {
        "Greeter": "//examples/claro_programs/module_system/emergent_inheritance_support/animals:Greeter",
    },
    exports = ["Greeter"],
    visibility = ["//examples/claro_programs/module_system/emergent_inheritance_support:__pkg__"],
)
# TODO(steving) "Inheritence" would be legitimately doable if module_api_file was concatenative like `srcs`.
genrule(
    name = "cat_api",
    outs = ["CatAnimal.claro_module_api"],
    srcs = [
        "Cat.claro_module_api",
        "//examples/claro_programs/module_system/emergent_inheritance_support/animals:Animal.claro_module_api",
    ],
    cmd = "cat $(SRCS) > $(OUTS)"
)
