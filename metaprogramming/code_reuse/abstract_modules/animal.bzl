# animal.bzl
load(
    "@claro-lang//stdlib/utils/abstract_modules:abstract_module.bzl",
    "claro_abstract_module",
)

Animal = \
    claro_abstract_module(
        name = "Animal",
        module_api_file = "animal.claro_module_api",
        overridable_srcs = {
            "AnimalSoundsImpl": ":default_animal_sounds_impl.claro",
            "InternalStateAndConstructor": ":default_internal_state.claro",
            "MakeNoiseImpl": ":default_make_noise_impl.claro",
        },
        default_deps = {
            "AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds",
        },
        default_exports = ["AnimalSounds"],
    )
