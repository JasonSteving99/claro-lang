load("@aspect_rules_js//js:defs.bzl", "js_library", "js_run_binary", "js_run_devserver", "js_test")
load("@aspect_rules_ts//ts:defs.bzl", "ts_config")
load("@npm//:defs.bzl", "npm_link_all_packages")
load("@npm//tools/clarodocs:eslint/package_json.bzl", eslint_bin = "bin")
load("@npm//tools/clarodocs:tsconfig-to-swcconfig/package_json.bzl", tsconfig_to_swcconfig = "bin")
load("@npm//tools/clarodocs:vite/package_json.bzl", "bin")

npm_link_all_packages()

eslint_bin.eslint_binary(name = "eslint")

RUNTIME_DEPS = [
    "index.html",
    "package.json",
    "//tools/clarodocs:node_modules/react-dom",
    "//tools/clarodocs:node_modules/react",
    "//tools/clarodocs:node_modules/web-vitals",
    "//tools/clarodocs/src:assets",
    "//tools/clarodocs/src",
    "//tools/clarodocs/public",
]

ts_config(
    name = "tsconfig",
    src = "tsconfig.json",
    visibility = ["//visibility:public"],
)

tsconfig_to_swcconfig.t2s(
    name = "write_swcrc",
    srcs = ["tsconfig.json"],
    args = [
        "--filename",
        "$(location tsconfig.json)",
    ],
    stdout = ".swcrc",
    visibility = ["//tools/clarodocs:__subpackages__"],
)

js_library(
    name = "vite.config",
    srcs = ["vite.config.js"],
    data = [
        "//tools/clarodocs:node_modules/@vitejs/plugin-react",
        "//tools/clarodocs:node_modules/vite-plugin-svgr",
        "//tools/clarodocs:node_modules/vitest",
    ],
    visibility = ["//tools/clarodocs:__subpackages__"],
)

bin.vite_binary(
    name = "vite",
    chdir = package_name(),
    data = ["vite.config"],
)

# Fast developer round-trip under ibazel
js_run_devserver(
    name = "start",
    args = ["."],
    data = RUNTIME_DEPS,
    tool = ":vite",
)

# Create production release artifacts
js_run_binary(
    name = "build",
    srcs = RUNTIME_DEPS,
    args = ["build"],
    mnemonic = "ViteBuild",
    out_dirs = ["dist"],
    tool = ":vite",
)

# Hosts the production-bundled application in a web server
bin.vite_binary(
    name = "preview",
    args = ["preview"],
    chdir = package_name(),
    data = [":build"],
)

# Just verify that the bundle produced "something reasonable" but doesn't verify it functions in a browser.
# TODO: use something like Cypress for a true end-to-end test
js_test(
    name = "build_smoke_test",
    timeout = "short",
    data = [":build"],
    entry_point = "build_smoke_test.js",
)
