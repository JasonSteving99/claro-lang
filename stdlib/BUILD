load("//src/java/com/claro:claro_build_rules_internal.bzl", "bootstrapped_claro_module_internal")

exports_files(glob(["*.claro_internal"]))

bootstrapped_claro_module_internal(
    name = "std",
    module_api_file = "std.claro_module_api",
    visibility = ["//visibility:public"],
)