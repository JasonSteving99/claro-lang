load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    "bootstrapped_claro_module_internal",
)

bootstrapped_claro_module_internal(
    name = "duration",
    module_api_file = "duration.claro_module_api",
    srcs = ["duration.claro_internal"],
    deps = {
        "instant": "@claro-lang//stdlib/time/instant:instant",
    },
    exports = ["instant"],
    visibility = ["//visibility:public"],
)
