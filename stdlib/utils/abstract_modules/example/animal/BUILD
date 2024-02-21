load("//:rules.bzl", "claro_module")

exports_files(glob(["*.claro*"], exclude = ["animal_sounds.claro_module_api"]))

claro_module(
    name = "animal_sounds",
    module_api_file = "animal_sounds.claro_module_api",
    visibility = [
        "//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis:__subpackages__",
        "//stdlib/utils/abstract_modules/example:__pkg__",
    ],
)