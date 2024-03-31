load("@aspect_rules_js//js:defs.bzl", "js_library")
load("@aspect_rules_ts//ts:defs.bzl", "ts_project")
load("//tools/clarodocs:lint.bzl", "eslint_test")
load("//tools/clarodocs:defs.bzl", "ASSET_PATTERNS", "SRC_PATTERNS", "TEST_PATTERNS", "TRANSPILER")

package(default_visibility = ["//visibility:public"])

js_library(
    name = "assets",
    srcs = glob(ASSET_PATTERNS),
)

ts_project(
    name = "src",
    srcs = glob(
        include = SRC_PATTERNS,
        exclude = TEST_PATTERNS,
    ),
    data = [":assets"],
    declaration = True,
    resolve_json_module = True,
    transpiler = TRANSPILER,
    tsconfig = "//tools/clarodocs:tsconfig",
    deps = [
        "//tools/clarodocs:node_modules/@types/react",
        "//tools/clarodocs:node_modules/antd",
        "//tools/clarodocs:node_modules/react",
        "//tools/clarodocs:node_modules/react-dom",
        "//tools/clarodocs:node_modules/vite-plugin-svgr",
        "//tools/clarodocs:node_modules/web-vitals",
        "//tools/clarodocs/src/components/dep_graph",
        "//tools/clarodocs/src/components/highlight_js",
        "//tools/clarodocs/src/components/input",
        "//tools/clarodocs/src/components/module_tree",
        "//tools/clarodocs/src/claro_module_apis",
        "//tools/clarodocs/src/hooks:use_search_params_hook",
    ],
)

# Test that fails if the lint report is non-empty
# Remove the `eslint-disable-next-line` line from index.tsx to see this test fail.
eslint_test(
    name = "lint",
    timeout = "short",
    srcs = [":src_typings"],
)
