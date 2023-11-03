load("//src/java/com/claro:claro_build_rules.bzl", "claro_module")

package(default_visibility = ["//examples/claro_programs/demo_server/buggy_buggies/endpoint_handlers:__pkg__"])

claro_module(
    name = "resources",
    module_api_file = "resources.claro_module_api",
    srcs = [
        "resource_providers.claro",
    ],
    resources = {"GamePageHtml": "game_page.html"},
    debug = True
)