load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    "bootstrapped_claro_module_internal",
    "claro_binary",
    "CLARO_STDLIB_MODULES",
)
load("//stdlib/utils/expand_template:expand_template.bzl", "expand_template")

expand_template(
    name = "test_expand",
    template = "test.tmpl",
    substitutions = {
        "SUB": "substitution.txt",
    },
    out = "formatted.claro",
)