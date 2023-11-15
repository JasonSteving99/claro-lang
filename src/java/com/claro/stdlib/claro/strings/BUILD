load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    "bootstrapped_claro_module_internal",
    "claro_binary",
)

bootstrapped_claro_module_internal(
    name = "strings",
    module_api_file = "strings.claro_module_api",
    srcs = ["strings.claro_internal"],
    visibility = ["//visibility:public"],
)

claro_binary(
    name = "test_strings",
    main_file = "test.claro",
)
