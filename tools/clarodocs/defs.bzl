"Constants for conventions used for React source files"

load("@aspect_rules_swc//swc:defs.bzl", "swc")
load("@bazel_skylib//lib:partial.bzl", "partial")

ASSET_PATTERNS = [
    "*.svg",
    "*.css",
]

SRC_PATTERNS = [
    "*.tsx",
    "*.ts",
]

RUNTIME_DEPS = [
    "//tools/clarodocs:runtime_deps",
    "//tools/clarodocs:node_modules/react-dom",
    "//tools/clarodocs:node_modules/react",
    "//tools/clarodocs:node_modules/web-vitals",
    "//tools/clarodocs/src:assets",
    "//tools/clarodocs/src",
    "//tools/clarodocs/public",
]

# Filename conventions described at
# https://create-react-app.dev/docs/running-tests#filename-conventions
TEST_PATTERNS = [
    "*.test.tsx",
    "*.test.ts",
    "*.spec.tsx",
    "*.spec.ts",
]

# Partially-apply our (generated) .swcrc config file to the swc starlark function
TRANSPILER = partial.make(
    swc,
    swcrc = "//tools/clarodocs:.swcrc",
)
