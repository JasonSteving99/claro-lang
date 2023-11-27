load("//src/java/com/claro/stdlib/utils/abstract_modules:abstract_module.bzl", "claro_abstract_module")

visibility(["//src/java/com/claro/stdlib/utils/abstract_modules/example/..."])

Animal = \
    claro_abstract_module(
        class_name = "Animal",
        module_api_file = "//src/java/com/claro/stdlib/utils/abstract_modules/example/animal:animal.claro_module_api",
        parameterized_type_names = ["State"],
        default_srcs = ["//src/java/com/claro/stdlib/utils/abstract_modules/example/animal:default_animal_impl.claro"],
        overridable_srcs = {
            "MakeNoiseImpl": "//src/java/com/claro/stdlib/utils/abstract_modules/example/animal:default_make_noise_impl.claro",
        },
        default_deps = {
            "AnimalSounds": "//src/java/com/claro/stdlib/utils/abstract_modules/example/animal:animal_sounds",
        },
        default_exports = ["AnimalSounds"],
    )
