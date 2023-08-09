load("//src/java/com/claro:claro_build_rules_internal.bzl", "claro_module_internal")

exports_files(glob(["*.claro_internal"]))

# Note: Naming of these stdlib module targets is actually important. The name of the target itself will be translated
#       directly into <name>.claro_module. The CLI makes use of this naming convention to automatically discover stdlib
#       modules packaged in the java deps jar. So, name the stdlib module targets carefully, as these are the names that
#       will be available for .claro srcs to use when built with the CLI.

claro_module_internal(
    name = "std",
    module_api_file = "std.claro_module_api",
    # TODO(steving) I need to upgrade the claro_module() target to allow api-only modules when only type defs are exported.
    srcs = [":noop_src"],
    visibility = ["//visibility:public"],
)

genrule(
    name = "test_stdlib_api",
    outs = ["std.claro_module_api"],
    srcs = ["//src/java/com/claro/stdlib/claro:builtin_types.claro_internal"],
    cmd = "cat $(SRCS) > $(OUTS)",
)

genrule(
    name = "noop_src",
    outs = ["noop_src.claro"],
    cmd = "echo '_ = 1;' > $(OUTS)",
)
