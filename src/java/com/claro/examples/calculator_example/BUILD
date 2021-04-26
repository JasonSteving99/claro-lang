package(default_visibility = ["//visibility:public"])

load(":java_calculator.bzl", "gen_calculator_compiler", "java_calculator_library", "java_calculator_image", "java_calculator_binary", "DEFAULT_CALCULATOR_NAME")
load("@io_bazel_rules_docker//docker:docker.bzl", "docker_push")

# Manually run this target if you're feelin fancy and want to push this binary
# to Docker Hub.
docker_push(
   name = "push_first_compiled_calculator_image",
   image = ":first_compiled_calculator_image",
   registry = "index.docker.io",
   repository = "clarolang/calculator",
   tag = "first",
)

# This thing is useless unless you wanna push to docker.
java_calculator_image(
    name = "first_compiled_calculator_image",
    srcs = [":first_compiled_calculator"],
    java_name = "First",
)

java_calculator_binary(
    name = "first_compiled_calculator_binary",
    srcs = [":first_compiled_calculator"],
    java_name = "First",
)

java_calculator_library(
    name = "first_compiled_calculator",
    src = "//src/java/com/claro/examples/calculator_example/calculator_programs:first.calculator",
    java_name = "First",
)

# Manually run this target if you're feelin fancy and want to push this binary
# to Docker Hub.
docker_push(
   name = "push_second_compiled_calculator_image",
   image = ":second_compiled_calculator_image",
   registry = "index.docker.io",
   repository = "clarolang/calculator",
   tag = "second",
)

java_calculator_image(
    name = "second_compiled_calculator_image",
    srcs = [":second_compiled_calculator"],
    java_name = "Second",
)

java_calculator_binary(
    name = "second_compiled_calculator_binary",
    srcs = [":second_compiled_calculator"],
    java_name = "Second",
)

java_calculator_library(
    name = "second_compiled_calculator",
    src = "//src/java/com/claro/examples/calculator_example/calculator_programs:second.calculator",
    java_name = "Second",
)


# Manually run this target if you're feelin fancy and want to push this binary
# to Docker Hub.
docker_push(
   name = "push_calculator_compiler_image",
   image = ":" + DEFAULT_CALCULATOR_NAME + "_compiler_image",
   registry = "index.docker.io",
   repository = "clarolang/calculator",
   tag = "compiler",
)

gen_calculator_compiler()
