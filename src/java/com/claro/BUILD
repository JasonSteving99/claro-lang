package(default_visibility = ["//visibility:public"])

load(":claro_build_rules.bzl", "gen_claro_compiler", "claro_library", "claro_image", "claro_binary", "DEFAULT_CLARO_NAME")
load("@io_bazel_rules_docker//docker:docker.bzl", "docker_push")

# Manually run this target if you're feelin fancy and want to push this binary
# to Docker Hub.
docker_push(
   name = "push_first_compiled_claro_image",
   image = ":first_compiled_claro_image",
   registry = "index.docker.io",
   repository = "clarolang/claro",
   tag = "first",
)

# This thing is useless unless you wanna push to docker.
claro_image(
    name = "first_compiled_claro_image",
    srcs = [":first_compiled_claro"],
    java_name = "First",
)

claro_binary(
    name = "first_compiled_claro_binary",
    srcs = [":first_compiled_claro"],
    java_name = "First",
)

claro_library(
    name = "first_compiled_claro",
    src = "//src/java/com/claro/claro_programs:first.claro",
    java_name = "First",
)

# Manually run this target if you're feelin fancy and want to push this binary
# to Docker Hub.
docker_push(
   name = "push_second_compiled_claro_image",
   image = ":second_compiled_claro_image",
   registry = "index.docker.io",
   repository = "clarolang/claro",
   tag = "second",
)

claro_image(
    name = "second_compiled_claro_image",
    srcs = [":second_compiled_claro"],
    java_name = "Second",
)

claro_binary(
    name = "second_compiled_claro_binary",
    srcs = [":second_compiled_claro"],
    java_name = "Second",
)

claro_library(
    name = "second_compiled_claro",
    src = "//src/java/com/claro/claro_programs:second.claro",
    java_name = "Second",
)


# Manually run this target if you're feelin fancy and want to push this binary
# to Docker Hub.
docker_push(
   name = "push_claro_compiler_image",
   image = ":" + DEFAULT_CLARO_NAME + "_compiler_image",
   registry = "index.docker.io",
   repository = "clarolang/claro",
   tag = "compiler",
)

gen_claro_compiler()
