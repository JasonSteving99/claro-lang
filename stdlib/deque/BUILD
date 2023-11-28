load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    "bootstrapped_claro_module_internal",
    "CLARO_STDLIB_MODULES",
)
load("//:rules.bzl", "claro_binary")

bootstrapped_claro_module_internal(
    name = "deque",
    module_api_file = "deque.claro_module_api",
    srcs = ["deque.claro_internal"],
    deps = {
        "std": CLARO_STDLIB_MODULES["std"],
    },
    exports = ["std"],
    visibility = ["//visibility:public"],
)

claro_binary(
    name = "test",
    main_file = "test.claro",
)