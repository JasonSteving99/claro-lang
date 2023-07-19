load("//src/java/com/claro:claro_build_rules.bzl", "claro_module")

# TODO(steving) This is only temporary. Long term, once Claro's Module system supports exporting newtype defs and
# TODO(steving)   initializers/unwrappers, then these .claro files should be properly exposed via modules.
exports_files(["reduce.claro"])

claro_module(
    name = "utils",
    module_api_file = "utils.claro_module_api",
    srcs = ["handle_buggy_response.claro"],
    visibility = ["//src/java/com/claro/claro_programs/demo_server/buggy_buggies:__subpackages__"],
)