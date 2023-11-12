load(
    ":claro_build_rules_internal.bzl",
    _gen_claro_builtin_java_deps_jar = "gen_claro_builtin_java_deps_jar",
)

visibility(["//examples/claro_programs"])

# TODO(steving) This should be removed from this public .bzl file. It would require updating the GitHub Workflow that
# TODO(steving)     expects the existence of this target used in "//examples/claro_programs:BUILD".
gen_claro_builtin_java_deps_jar = _gen_claro_builtin_java_deps_jar