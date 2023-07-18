load("//src/java/com/claro:claro_build_rules.bzl", "claro_binary")


# This is an in-progress refactoring of Claro's first demo server into something that resembles a well-organized
# source code structure.
# TODO(steving) Currently there are still some sacrifices because Claro's First-Class Module System is not fully
# TODO(steving)   implemented. We still need:
# TODO(steving)     - exported `newtype` defs
# TODO(steving)     - exported `alias` defs
# TODO(steving)     - exported `HttpServices`
# TODO(steving)     - exported `initializers` and `unwrappers`
# TODO(steving)   Once all of these are in place, then this implementation can be fully decomposed into a DAG of
# TODO(steving)   claro_module() targets which would be a good demonstration of building well organized programs in Claro.
claro_binary(
    name = "buggy_buggies_http_server",
    main_file = "buggy_buggies_http_server.claro",
    # TODO(steving) These should be extracted into claro_module() dep targets for proper namespacing once possible.
    srcs = [
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/buggy_agent:agent.claro",
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/buggy_agent:dijkstra.claro",
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/buggy_buggies_service:buggy_buggies_move_response.claro",
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/buggy_buggies_service:buggy_buggies_service.claro",
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/data_structures:default_dict.claro",
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/data_structures:heap.claro",
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/endpoint_handlers:game_move_handler.claro",
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/endpoint_handlers:game_page_handler.claro",
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/endpoint_handlers:get_best_moves_handler.claro",
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/endpoint_handlers:start_new_game_handler.claro",
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/utils:handle_buggy_response.claro",
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/utils:reduce.claro",
    ],
    deps = {
        # ... TODO(steving) Populate with real module deps.
    },
)