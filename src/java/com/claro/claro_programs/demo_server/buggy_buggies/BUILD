load("//src/java/com/claro:claro_build_rules.bzl", "claro_binary")
load("//src/java/com/claro/module_system/clarodocs:clarodocs_rules.bzl", "clarodocs")


# This is an in-progress refactoring of Claro's first demo server into something that resembles a well-organized
# source code structure.
claro_binary(
    name = "buggy_buggies_http_server",
    main_file = "buggy_buggies_http_server.claro",
    deps = {
        "BuggyBuggies": "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/buggy_buggies_service:buggy_buggies_client",
        "EndpointHandlers": "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/endpoint_handlers:endpoint_handlers",
        "Utils": "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/utils:utils",
    },
    optional_stdlib_deps = ["http"],
)

clarodocs(
    name = "buggy_buggies_docs",
    root = ":buggy_buggies_http_server",
)