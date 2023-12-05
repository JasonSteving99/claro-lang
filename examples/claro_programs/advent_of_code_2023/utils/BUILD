load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    "bootstrapped_claro_module_internal",
    "CLARO_STDLIB_MODULES",
)
load("//:rules.bzl", "claro_module")

claro_module(
    name = "utils",
    module_api_file = "utils.claro_module_api",
    srcs = ["utils.claro"],
    visibility = ["//examples/claro_programs/advent_of_code_2023:__subpackages__"],
)

# TODO(steving) THIS IS A TEMPORARY HACK FOR ME TO COLLECT FEATURES THAT I DISCOVER ARE NECESSARY DURING AoC.
bootstrapped_claro_module_internal(
    name = "hacking_utils",
    module_api_file = "hacking_utils.claro_module_api",
    srcs = ["hacking_utils.claro_internal"],
    visibility = [
        # DAY 5 REASON: I needed access to the `long` primitive data type....currently Claro only has support for `int`.
        "//examples/claro_programs/advent_of_code_2023/day_05:__pkg__",
    ],
    deps = {
        "std": CLARO_STDLIB_MODULES["std"],
    },
    exports = ["std"],
    debug = True,
)