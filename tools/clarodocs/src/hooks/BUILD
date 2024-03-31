load("@aspect_rules_ts//ts:defs.bzl", "ts_project")
load("//tools/clarodocs:defs.bzl", "TRANSPILER")

ts_project(
    name = "use_search_params_hook",
    srcs = ["UseSearchParamsHook.tsx"],
    declaration = True,
    resolve_json_module = True,
    transpiler = TRANSPILER,
    tsconfig = "//tools/clarodocs:tsconfig",
    visibility = ["//tools/clarodocs:__subpackages__"],
    deps = [
        "//tools/clarodocs:node_modules/react-router-dom",
    ],
)
