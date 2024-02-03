load("@claro-lang//:rules.bzl", "claro_module", "claro_binary")

exports_files([
    "contract_impl.claro",
    "example_api_defs.claro_module_api",
    "pretty_print.claro",
    "test.claro",
])

genrule(
    name = "concat_api",
    outs = ["example.claro_module_api"],
    srcs = ["example_api_defs.claro_module_api", "example_api_stubs.claro_module_api"],
    cmd = "cat $(location example_api_defs.claro_module_api) $(location example_api_stubs.claro_module_api) > $(OUTS)",
    visibility = ["//mdbook_docs/src/module_system:__pkg__"],
)

claro_module(
    name = "example",
    module_api_file = "example.claro_module_api",
    srcs = [
        "contract_impl.claro",
        "pretty_print.claro",
    ],
    deps = {
        "Boxes": ":box",
    },
    visibility = ["//visibility:public"],
)

claro_module(
    name = "box",
    module_api_file = "boxes.claro_module_api",
    srcs = ["boxes.claro"],
    visibility = ["//mdbook_docs/src/module_system:__pkg__"],
)

claro_binary(
    name = "test",
    main_file = "test.claro",
    deps = {
        "Ex": ":example",
    },
)