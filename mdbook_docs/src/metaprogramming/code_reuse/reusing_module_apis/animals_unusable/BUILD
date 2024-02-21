# BUILD
load("@claro-lang//:rules.bzl", "claro_module", "claro_binary")
exports_files(["BUILD", "animal.claro_module_api"], visibility = ["//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis:__pkg__"])

claro_module(
    name = "dog",
    module_api_file = "animal.claro_module_api",
    srcs = ["dog.claro"],
    deps = {"AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds"},
    # This Module is referenced in this Module's API so must be exported.
    exports = ["AnimalSounds"],
)
claro_module(
    name = "cat",
    module_api_file = "animal.claro_module_api",
    srcs = ["cat.claro"],
    deps = {"AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds"},
    # This Module is referenced in this Module's API so must be exported.
    exports = ["AnimalSounds"],
)
# ...