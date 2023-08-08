load("//src/java/com/claro:claro_build_rules.bzl", "claro_module_internal")

exports_files(glob(["*.claro_internal"]))

claro_module_internal(
    name = "builtin_types",
    module_api_file = "test_stdlib.claro_module_api",
    # TODO(steving) I need to upgrade the claro_module() target to allow api-only modules when only type defs are exported.
    srcs = [":noop_src"],
    visibility = ["//visibility:public"],
)

genrule(
    name = "test_stdlib_api",
    outs = ["test_stdlib.claro_module_api"],
    srcs = ["//src/java/com/claro/stdlib/claro:builtin_types.claro_internal"],
    cmd = "cat $(SRCS) > $(OUTS)",
)

genrule(
    name = "noop_src",
    outs = ["noop_src.claro"],
    cmd = "echo '_ = 1;' > $(OUTS)",
)
