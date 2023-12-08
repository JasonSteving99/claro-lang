load("@claro-lang//:rules.bzl", "claro_binary", "claro_module")

# Shared logic used by both part1 and part2 extracted to this module.
claro_module(
    name = "common",
    module_api_file = "common.claro_module_api",
    srcs = ["common.claro"],
    resources = {
        "Input": "input.txt",
    },
)

# Builds a single executable that can run both part 1 and part 2. Runs part 1 by default, and part 2 by passing the
# --wild command line flag used by the module above. You can do that in Bazel as so:
#   $ bazel run //examples/claro_programs/advent_of_code_2023/day_07:parts_1_and_2 -- --wild
claro_binary(
    name = "parts_1_and_2",
    main_file = "parts_1_and_2.claro",
    deps = {
        "Common": ":common",
        "Utils": "//examples/claro_programs/advent_of_code_2023/utils:utils",
    },
)
