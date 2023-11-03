load("//src/java/com/claro:claro_build_rules.bzl", "claro_module")


claro_module(
    name = "Dog",
    module_api_file = "DogAnimal.claro_module_api",
    srcs = [
        "DogAnimal.claro",
        "//examples/claro_programs/modules/emergent_inheritance_support/animals:Animal.claro",
    ],
    deps = {
        "Greeter": "//examples/claro_programs/modules/emergent_inheritance_support/animals:Greeter",
    },
    exports = ["Greeter"],
    visibility = ["//examples/claro_programs/modules/emergent_inheritance_support:__pkg__"],
)
# TODO(steving) "Inheritence" would be legitimately doable if module_api_file was concatenative like `srcs`.
genrule(
    name = "dog_api",
    outs = ["DogAnimal.claro_module_api"],
    srcs = [
        "Dog.claro_module_api",
        "//examples/claro_programs/modules/emergent_inheritance_support/animals:Animal.claro_module_api",
    ],
    cmd = "cat $(SRCS) > $(OUTS)"
)
