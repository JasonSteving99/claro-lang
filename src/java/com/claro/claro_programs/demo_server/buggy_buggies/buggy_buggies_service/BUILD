load("//src/java/com/claro:claro_build_rules.bzl", "claro_module")


claro_module(
    name = "buggy_buggies_client",
    module_api_file = "buggy_buggies_client.claro_module_api",
    srcs = [
        "buggy_buggies_client.claro",
        "buggy_buggies_move_response.claro",
    ],
    visibility = [
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies:__pkg__",
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/endpoint_handlers:__pkg__",
    ],
    debug = True
)