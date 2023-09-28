load("//src/java/com/claro:claro_build_rules.bzl", "claro_module")


claro_module(
    name = "Cat",
    module_api_file = "CatAnimal.claro_module_api",
    srcs = [
        "CatAnimal.claro",
        "//src/java/com/claro/claro_programs/modules/emergent_inheritance_support/animals:Animal.claro",
    ],
    deps = {
        "Greeter": "//src/java/com/claro/claro_programs/modules/emergent_inheritance_support/animals:Greeter",
    },
    exports = ["Greeter"],
    visibility = ["//src/java/com/claro/claro_programs/modules/emergent_inheritance_support:__pkg__"],
)
# TODO(steving) "Inheritence" would be legitimately doable if module_api_file was concatenative like `srcs`.
genrule(
    name = "cat_api",
    outs = ["CatAnimal.claro_module_api"],
    srcs = [
        "Cat.claro_module_api",
        "//src/java/com/claro/claro_programs/modules/emergent_inheritance_support/animals:Animal.claro_module_api",
    ],
    cmd = "cat $(SRCS) > $(OUTS)"
)
