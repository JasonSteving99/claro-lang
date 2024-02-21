# BUILD
load("@claro-lang//:rules.bzl", "claro_module", "claro_binary")
exports_files(["BUILD", "animal.claro_module_api"], visibility = ["//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis:__pkg__"])

genrule(
    name = "dog_api",
    srcs = ["animal.claro_module_api", "dog_cons.claro_module_api"],
    outs = ["dog.claro_module_api"],
    cmd = "cat $(SRCS) > $(OUTS)"
)
claro_module(
    name = "Dog",
    module_api_file = ":dog_api", # Updated to use the API with a constructor.
    srcs = ["dog.claro"],
    deps = {"AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds"},
    # This Module is referenced in this Module's API so must be exported.
    exports = ["AnimalSounds"],
    visibility = ["//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis:__pkg__"],
)

genrule(
    name = "cat_api",
    srcs = ["animal.claro_module_api", "cat_cons.claro_module_api"],
    outs = ["cat.claro_module_api"],
    cmd = "cat $(SRCS) > $(OUTS)"
)
claro_module(
    name = "Cat",
    module_api_file = ":cat_api", # Updated to use the API with a constructor.
    srcs = ["cat.claro"],
    deps = {"AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds"},
    # This Module is referenced in this Module's API so must be exported.
    exports = ["AnimalSounds"],
    visibility = ["//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis:__pkg__"],
)
# ...
