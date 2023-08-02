load("//src/java/com/claro:claro_build_rules.bzl", "claro_module")

# TODO(steving) This is only temporary. Long term, once Claro's Module system supports exporting generic procedure
# TODO(steving)   definitions that can get monomorphized at the claro_binary() then this .claro file should be properly
# TODO(steving)   exposed by a claro_module() target.
exports_files(glob(["default_dict.claro"]))

claro_module(
    name = "heap",
    module_api_file = "heap.claro_module_api",
    srcs = ["heap.claro"],
    visibility = [
        "//src/java/com/claro/claro_programs/demo_server/buggy_buggies/buggy_agent:__pkg__"
    ],
    debug = True,
)