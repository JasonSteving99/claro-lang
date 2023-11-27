load("//src/java/com/claro:claro_build_rules_internal.bzl", "bootstrapped_claro_module_internal")

exports_files(glob(["*.claro_internal"]))

# Note: Naming of these stdlib module targets is actually important. The name of the target itself will be translated
#       directly into <name>.claro_module. The CLI makes use of this naming convention to automatically discover stdlib
#       modules packaged in the java deps jar. So, name the stdlib module targets carefully, as these are the names that
#       will be available for .claro srcs to use when built with the CLI.

bootstrapped_claro_module_internal(
    name = "std",
    module_api_file = "std.claro_module_api",
    visibility = ["//visibility:public"],
)