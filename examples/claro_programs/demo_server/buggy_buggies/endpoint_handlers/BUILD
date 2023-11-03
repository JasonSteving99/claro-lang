load("//src/java/com/claro:claro_build_rules.bzl", "claro_module")

package(default_visibility = ["//examples/claro_programs/demo_server/buggy_buggies:__pkg__"])

claro_module(
    name = "endpoint_handlers",
    module_api_file = "endpoint_handlers.claro_module_api",
    srcs = [
        "game_move_handler.claro",
        "game_page_handler.claro",
        "get_best_moves_handler.claro",
        "start_new_game_handler.claro",
    ],
    deps = {
        "Agent": "//examples/claro_programs/demo_server/buggy_buggies/buggy_agent:buggy_agent",
        "BuggyBuggies": "//examples/claro_programs/demo_server/buggy_buggies/buggy_buggies_service:buggy_buggies_client",
        "Utils": "//examples/claro_programs/demo_server/buggy_buggies/utils:utils",
        "Pos": "//examples/claro_programs/demo_server/buggy_buggies/data_structures:position",
        "Resources": "//examples/claro_programs/demo_server/buggy_buggies/endpoint_handlers/resources:resources",
    },
)