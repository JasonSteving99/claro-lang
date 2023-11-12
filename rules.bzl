load(
    "@claro-lang//src/java/com/claro:claro_build_rules_internal.bzl",
    _claro_module = "claro_module",
    _claro_binary = "claro_binary",
)
load(
    "//src/java/com/claro/module_system/clarodocs:clarodocs_rules.bzl",
    _claro_docs = "clarodocs",
)
load(
    "@claro-lang//src/java/com/claro/stdlib/utils/abstract_modules:abstract_module.bzl",
    _claro_abstract_module = "claro_abstract_module",
)

# This is the officially supported entrypoint to Claro's Bazel Build rules.
visibility("public")

claro_module = _claro_module
claro_abstract_module = _claro_abstract_module
claro_binary = _claro_binary
claro_docs = _claro_docs
