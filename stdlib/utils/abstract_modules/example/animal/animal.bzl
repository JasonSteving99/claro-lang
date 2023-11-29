load("//stdlib/utils/abstract_modules:abstract_module.bzl", "claro_abstract_module")

visibility(["//stdlib/utils/abstract_modules/example/..."])

Animal = \
    claro_abstract_module(
        class_name = "Animal",
        module_api_file = "//stdlib/utils/abstract_modules/example/animal:animal.claro_module_api",
        parameterized_type_names = ["State"],
        default_srcs = ["//stdlib/utils/abstract_modules/example/animal:default_animal_impl.claro"],
        overridable_srcs = {
            "MakeNoiseImpl": "//stdlib/utils/abstract_modules/example/animal:default_make_noise_impl.claro",
        },
        default_deps = {
            "AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds",
        },
        default_exports = ["AnimalSounds"],
    )
