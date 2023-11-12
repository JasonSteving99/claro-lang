load(
    ":claro_build_rules_internal.bzl",
    _claro_binary = "claro_binary",
    _claro_module = "claro_module",
    _gen_claro_builtin_java_deps_jar = "gen_claro_builtin_java_deps_jar",
)
load(
    "//src/java/com/claro/module_system/clarodocs:clarodocs_rules.bzl",
    _claro_docs = "clarodocs",
)

visibility("public")

# This module simply re-exports some subset of the internal-only build rules so that public usage of Claro's build rules
# is restricted to the set of rules that have been intentionally crafted for public consumption.
claro_module = _claro_module
claro_binary = _claro_binary
claro_docs = _claro_docs

# TODO(steving) This should be removed from this public .bzl file. It would require updating the GitHub Workflow that
# TODO(steving)     expects the existence of this target used in "//examples/claro_programs:BUILD".
gen_claro_builtin_java_deps_jar = _gen_claro_builtin_java_deps_jar