load("//:rules.bzl", "claro_module", "claro_binary")


# TODO(steving) Realistically I'd rather break up this functionality into two separate modules. In particular I don't
# TODO(steving)   think the dijkstra logic needs to be exposed to anyone outside this package. Unfortunately, in order
# TODO(steving)   to simplify the task of agent.claro and dijkstra.claro "speaking the same language" in regards to the
# TODO(steving)   `newtype Position` def, I'm compiling these within the same compilation unit. Split this out once it's
# TODO(steving)   possible to ensure that everyone speaks the same language with regards to types defined in transitive
# TODO(steving)   dep modules.
claro_module(
    name = "buggy_agent",
    module_api_file = "buggy_agent.claro_module_api",
    srcs = [
        "agent.claro",
        "dijkstra.claro",
    ],
    deps = {
        "DefaultDict": "//examples/claro_programs/demo_server/buggy_buggies/data_structures:default_dict",
        "Heaps": "//examples/claro_programs/demo_server/buggy_buggies/data_structures:heap",
        "Pos": "//examples/claro_programs/demo_server/buggy_buggies/data_structures:position",
    },
    exports = ["Pos"],
    # Claro doesn't need to include any Java-like visibility controls at the language level as Bazel provides even finer
    # grained functionality out of the box.
    visibility = ["//examples/claro_programs/demo_server/buggy_buggies/endpoint_handlers:__pkg__"],
)