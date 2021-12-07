package(default_visibility = ["//visibility:public"])

load(":claro_build_rules.bzl", "gen_claro_compiler", "DEFAULT_CLARO_NAME")
load("@io_bazel_rules_docker//docker:docker.bzl", "docker_push")

# Manually run this target if you're feelin fancy and want to push this binary
# to Docker Hub.
docker_push(
   name = "push_claro_compiler_image",
   image = ":" + DEFAULT_CLARO_NAME + "_compiler_image",
   registry = "index.docker.io",
   repository = "clarolang/claro",
   tag = "compiler",
)

# Generate the targets for the Claro compiler itself.
gen_claro_compiler()
