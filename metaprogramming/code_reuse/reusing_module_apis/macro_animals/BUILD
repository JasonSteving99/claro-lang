# BUILD
load(":animals.bzl", "Animal")
exports_files(glob(["*"]), visibility = ["//mdbook_docs/src/metaprogramming/code_reuse:__subpackages__"])

Animal(name = "dog", srcs = ["dog.claro"])
Animal(name = "cat", srcs = ["cat.claro"])