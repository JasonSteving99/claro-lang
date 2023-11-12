load("//:rules.bzl", "claro_module")
load("@bazel_skylib//rules:common_settings.bzl", "string_flag")

claro_module(
    name = "messaging",
    module_api_file = "messaging.claro_module_api",
    srcs = select({
        ":dev": ["DEV_subject_provider.claro"],
        ":prod": ["PROD_subject_provider.claro"],
        ":runtime": ["RUNTIME_subject_provider.claro"],
    }),
    visibility = ["//examples/claro_programs/modules/demo_configurability:__pkg__"]
)

string_flag(
    name = "compile_time_env",
    values = [
        "dev",
        "prod",
        "runtime",
    ],
    build_setting_default = "dev",
)
config_setting(
    name = "dev",
    flag_values = {":compile_time_env": "dev"}
)
config_setting(
    name = "prod",
    flag_values = {":compile_time_env": "prod"}
)
config_setting(
    name = "runtime",
    flag_values = {":compile_time_env": "runtime"}
)
