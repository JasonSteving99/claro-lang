load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    "bootstrapped_claro_module_internal",
    "claro_binary",
)

bootstrapped_claro_module_internal(
    name = "string_builder",
    module_api_file = "string_builder.claro_module_api",
    srcs = ["string_builder.claro_internal"],
    visibility = ["//visibility:public"],
)

claro_binary(
    name = "test_string_builder",
    main_file = "test.claro",
)