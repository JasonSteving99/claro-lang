load("@claro-lang//:rules.bzl", "claro_binary", "claro_module")

# Get the real input at: https://adventofcode.com/2023/day/6

# Shared logic used by both part1 and part2 extracted to this module.
claro_module(
    name = "common",
    module_api_file = "common.claro_module_api",
    srcs = ["common.claro"],
    resources = {
        "Input": "input.txt",
    },
)

claro_binary(
    name = "part1",
    main_file = "part1.claro",
    deps = {
        "Common": ":common",
        "Utils": "//examples/claro_programs/advent_of_code_2023/utils:utils",
    },
)

claro_binary(
    name = "part2",
    main_file = "part2.claro",
    deps = {
        "Common": ":common",
        "Utils": "//examples/claro_programs/advent_of_code_2023/utils:utils",
    },
)
