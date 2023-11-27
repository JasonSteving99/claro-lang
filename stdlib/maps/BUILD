load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    "bootstrapped_claro_module_internal",
    "CLARO_STDLIB_MODULES")

bootstrapped_claro_module_internal(
    name = "maps",
    module_api_file = "maps.claro_module_api",
    srcs = ["maps.claro_internal"],
    # Stdlib Modules do not automatically receive deps on other stdlib modules, so in this case I need to manually
    # take a dep on the stdlib modules this one makes use of.
    deps = {
        "std" : CLARO_STDLIB_MODULES["std"],
    },
    exports = ["std"],
    visibility = ["//visibility:public"],
)