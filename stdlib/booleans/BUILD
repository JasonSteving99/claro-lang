load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    "bootstrapped_claro_module_internal",
)
load("//:rules.bzl", "claro_binary")

bootstrapped_claro_module_internal(
    name = "booleans",
    module_api_file = "booleans.claro_module_api",
    srcs = ["booleans.claro_internal"],
    visibility = ["//visibility:public"],
)


claro_binary(
    name = "test",
    main_file = "test.claro",
)