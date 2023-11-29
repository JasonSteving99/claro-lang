load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    "bootstrapped_claro_module_internal",
    "CLARO_STDLIB_MODULES",
)
load("//:rules.bzl", "claro_binary")

bootstrapped_claro_module_internal(
    name = "futures",
    module_api_file = "futures.claro_module_api",
    srcs = ["futures.claro_internal"],
    deps = {
        "duration": "@claro-lang//stdlib/time/duration:duration",
        "std": CLARO_STDLIB_MODULES["std"],
    },
    exports = ["duration", "std"],
    visibility = ["//visibility:public"],
)

claro_binary(
    name = "test",
    main_file = "test.claro",
    deps = {
        "duration": "//stdlib/time/duration:duration",
        "instant": "//stdlib/time/instant:instant",
    },
)