load("@aspect_rules_ts//ts:defs.bzl", "ts_project")
load("//tools/clarodocs:defs.bzl", "TRANSPILER")

ts_project(
    name = "module_tree",
    srcs = ["ModuleTree.tsx"],
    declaration = True,
    resolve_json_module = True,
    transpiler = TRANSPILER,
    tsconfig = "//tools/clarodocs:tsconfig",
    visibility = ["//tools/clarodocs:__subpackages__"],
    deps = [
        "//tools/clarodocs:node_modules/antd",
        "//tools/clarodocs:node_modules/@ant-design/icons",
        "//tools/clarodocs:node_modules/@types/react",
        "//tools/clarodocs:node_modules/react",
        "//tools/clarodocs/src/claro_module_apis",
    ],
)
