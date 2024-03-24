load("@aspect_rules_ts//ts:defs.bzl", "ts_project")
load("//tools/clarodocs:defs.bzl", "TRANSPILER")

ts_project(
    name = "claro_module_apis",
    srcs = glob(["*.tsx"]),
    declaration = True,
    resolve_json_module = True,
    transpiler = TRANSPILER,
    tsconfig = "//tools/clarodocs:tsconfig",
    deps = [
        "//tools/clarodocs:node_modules/react",
    ],
    visibility = ["//tools/clarodocs:__subpackages__"],
)
