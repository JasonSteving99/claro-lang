load("//src/java/com/claro:claro_build_rules_internal.bzl", "bootstrapped_claro_module_internal")
load("//:rules.bzl", "claro_binary")

bootstrapped_claro_module_internal(
    name = "math",
    module_api_file = "math.claro_module_api",
    srcs = ["math.claro_internal"],
    visibility = ["//visibility:public"],
)

claro_binary(
    name = "test",
    main_file = "test.claro",
)