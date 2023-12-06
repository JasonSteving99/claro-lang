load("@claro-lang//:rules.bzl", "claro_binary", "claro_module")


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
