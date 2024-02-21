# BUILD
load(":animals.bzl", "Animal")
exports_files(["animals.bzl", "BUILD"], visibility = ["//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis:__pkg__"])

Animal(name = "Dog", srcs = ["dog.claro"])
Animal(name = "Cat", srcs = ["cat.claro"])