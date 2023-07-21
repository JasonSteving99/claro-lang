load("//src/java/com/claro:claro_build_rules.bzl", "claro_module")

# TODO(steving) This is only temporary. Long term, once Claro's Module system supports exporting newtype defs and
# TODO(steving)   initializers/unwrappers, then these .claro files should be properly exposed via modules.
exports_files(glob(["buggy_buggies_service.claro"]))


claro_module(
    name = "buggy_buggies_client",
    module_api_file = "buggy_buggies_client.claro_module_api",
    srcs = ["buggy_buggies_move_response.claro"],
    visibility = ["//src/java/com/claro/claro_programs/demo_server/buggy_buggies/endpoint_handlers:__pkg__"],
)