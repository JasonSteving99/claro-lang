load("@aspect_rules_ts//ts:defs.bzl", "ts_project")
load("//tools/clarodocs:defs.bzl", "TRANSPILER")

ts_project(
    name = "dep_graph",
    srcs = ["DepGraph.tsx", "WholeProgramDepGraph.tsx"],
    declaration = True,
    resolve_json_module = True,
    transpiler = TRANSPILER,
    tsconfig = "//tools/clarodocs:tsconfig",
    visibility = ["//tools/clarodocs:__subpackages__"],
    deps = [
        "//tools/clarodocs/src/components/mermaid",
    ],
)
