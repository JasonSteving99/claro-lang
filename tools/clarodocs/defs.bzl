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
    "@claro-lang//tools/clarodocs:runtime_deps",
    "@claro-lang//tools/clarodocs:node_modules/react-dom",
    "@claro-lang//tools/clarodocs:node_modules/react",
    "@claro-lang//tools/clarodocs:node_modules/web-vitals",
    "@claro-lang//tools/clarodocs/src:assets",
    "@claro-lang//tools/clarodocs/src",
    "@claro-lang//tools/clarodocs/public",
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
    swcrc = "@claro-lang//tools/clarodocs:.swcrc",
)
