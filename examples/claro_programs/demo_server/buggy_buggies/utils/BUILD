load("//:rules.bzl", "claro_module")

claro_module(
    name = "utils",
    module_api_file = "utils.claro_module_api",
    srcs = [
        "handle_buggy_response.claro",
        "reduce.claro",
    ],
    visibility = ["//examples/claro_programs/demo_server/buggy_buggies:__subpackages__"],
)