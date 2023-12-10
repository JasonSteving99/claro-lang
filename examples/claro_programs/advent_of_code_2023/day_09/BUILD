load("@claro-lang//:rules.bzl", "claro_binary")

claro_binary(
    name = "parts_1_and_2",
    main_file = "parts_1_and_2.claro",
    resources = {
        "Input": "input.txt",
    },
    deps = {
        "Utils": "//examples/claro_programs/advent_of_code_2023/utils:utils",
    }
)