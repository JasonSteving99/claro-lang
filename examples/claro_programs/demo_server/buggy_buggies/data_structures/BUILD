load("//src/java/com/claro:claro_build_rules.bzl", "claro_module")

claro_module(
    name = "default_dict",
    module_api_file = "default_dict.claro_module_api",
    srcs = ["default_dict.claro"],
    visibility = [
        "//examples/claro_programs/demo_server/buggy_buggies/buggy_agent:__pkg__"
    ],
)

claro_module(
    name = "heap",
    module_api_file = "heap.claro_module_api",
    srcs = ["heap.claro"],
    deps = {
        "Pos": ":position"
    },
    exports = ["Pos"],
    visibility = [
        "//examples/claro_programs/demo_server/buggy_buggies/buggy_agent:__pkg__"
    ],
)

claro_module(
    name = "position",
    module_api_file = "position.claro_module_api",
    # TODO(steving) I need to upgrade the claro_module() target to allow api-only modules when only type defs are exported.
    srcs = [":noop_src"],
    visibility = [
        "//examples/claro_programs/demo_server/buggy_buggies:__subpackages__",
    ],
)

genrule(
    name = "noop_src",
    outs = ["noop_src.claro"],
    cmd = "echo '_ = 1;' > $(OUTS)",
)