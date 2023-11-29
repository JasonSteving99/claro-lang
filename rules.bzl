load(
    "@claro-lang//src/java/com/claro:claro_build_rules_internal.bzl",
    _claro_module = "claro_module",
    _claro_binary = "claro_binary",
)
load(
    "@claro-lang//stdlib/utils/abstract_modules:abstract_module.bzl",
    _claro_abstract_module = "claro_abstract_module",
)

# This is the officially supported entrypoint to Claro's Bazel Build rules.
visibility("public")

claro_module = _claro_module
claro_abstract_module = _claro_abstract_module
claro_binary = _claro_binary
