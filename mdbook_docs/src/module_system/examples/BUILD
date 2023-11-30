load("@claro-lang//:rules.bzl", "claro_module", "claro_binary")

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
)

claro_binary(
    name = "test",
    main_file = "test.claro",
    deps = {
        "Ex": ":example",
    },
)