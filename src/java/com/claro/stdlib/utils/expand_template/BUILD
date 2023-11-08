load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    "bootstrapped_claro_module_internal",
    "claro_binary",
    "CLARO_STDLIB_MODULES",
)
load("//src/java/com/claro/stdlib/utils/expand_template:expand_template.bzl", "expand_template")

# TODO(steving) This is only necessary because Claro doesn't yet have a `strings` stdlib module that provides
# TODO(steving)     `strings::replaceAll()`. This should be dropped when that's available.
bootstrapped_claro_module_internal(
    name = "expand_template_util",
    module_api_file = "expand_template_util.claro_module_api",
    srcs = ["expand_template_util.claro_internal"],
    visibility = ["//visibility:public"],
)


expand_template(
    name = "test_expand",
    template = "test.tmpl",
    substitutions = {
        "SUB": "substitution.txt",
    },
    out = "formatted.claro",
)