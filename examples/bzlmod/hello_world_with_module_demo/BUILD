load("@claro-lang//:rules.bzl", "claro_binary", "claro_module")
load("@claro-lang//tools/clarodocs/generator:clarodocs_rules.bzl", "clarodocs")


claro_module(
  name = "hello_world",
  module_api_file = "hello_world.claro_module_api",
  srcs = ["hello_world.claro"],
)


claro_binary(
  name = "hello_world_binary",
  main_file = "test_hello_world.claro",
  deps = {
    "Hello": ":hello_world",
  }
)

# It's recommended to run this using `$ ibazel run hello_world_docs`. This way, you should be able to just keep the
# generated site open throughout the duration of your development session and ibazel will auto-rebuild the
# :hello_world_binary program as you save changes, and the site will automatically refresh with the latest dep graph and
# module APIs.
#
# Find instructions to install ibazel at: https://github.com/bazelbuild/bazel-watcher.
#
# You can use this `clarodocs(...)` rule to generate a site for arbitrary `claro_binary(...)` targets.
clarodocs(
    name = "hello_world_docs",
    root = ":hello_world_binary",
)