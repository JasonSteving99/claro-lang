load("//src/java/com/claro:claro_build_rules.bzl", "claro_module")
load("@bazel_skylib//rules:common_settings.bzl", "string_flag")

SRCS = ["buggy_buggies_move_response.claro"]

# This example now demonstrates how Claro programs can leverage Bazel's builtin functionality to satisfy arbitrary
# configuration needs without the language itself being burdened with syntax for such a thing. This is a demonstration
# of compile-time configurability that comes with no runtime overhead of checking flags and such as you'd find in other
# existing Guice-like dependency injection frameworks.
claro_module(
    name = "buggy_buggies_client",
    module_api_file = "buggy_buggies_client.claro_module_api",
    srcs = select({
        ":dev": ["buggy_buggies_client_DEV.claro"] + SRCS,
        ":prod": ["buggy_buggies_client_PROD.claro"] + SRCS,
    }),
    optional_stdlib_deps = ["http"],
    visibility = [
        "//examples/claro_programs/demo_server/buggy_buggies:__pkg__",
        "//examples/claro_programs/demo_server/buggy_buggies/endpoint_handlers:__pkg__",
    ],
)

# For demonstration purposes only, I'm defining a brand new flag here, so that it's obvious where all of the moving
# pieces in the above claro_module() target come from.
#
# If you want to actually override the default flag value, set following flag in your `bazel run` command:
#   --//examples/claro_programs/demo_server/buggy_buggies/buggy_buggies_service:env_flag=dev
string_flag(
    name = "env_flag",
    values = [
        "dev",
        "prod",
    ],
    build_setting_default = "prod",
)
config_setting(
    name = "dev",
    flag_values = {
        ":env_flag": "dev"
    }
)
config_setting(
    name = "prod",
    flag_values = {
        ":env_flag": "prod"
    }
)
