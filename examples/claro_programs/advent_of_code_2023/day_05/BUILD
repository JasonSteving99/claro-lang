load("@claro-lang//:rules.bzl", "claro_binary")

# Get the real input at: https://adventofcode.com/2023/day/5

claro_binary(
    name = "part1",
    main_file = "part1.claro",
    resources = {
        "Input": "input.txt",
    },
    deps = {
        # TODO(steving) THIS CURRENT SOLUTION IS AN ABOMINATION. IT'S CURRENTLY MAKING A MASSIVE HACK AROUND CLARO'S
        # TODO(steving)   CURRENT LIMITATIONS IN ITS SET OF AVAILABLE BUILTIN PRIMITIVE TYPES - NAMELY THAT IT DOESN'T
        # TODO(steving)   HAVE ACCESS TO `long` AND ONLY HAS ACCESS TO `int`. THERE'S NO DEEP REASON FOR THIS BEYOND
        # TODO(steving)   THE FACT THAT I HAVEN'T HAD TIME TO ADD SUPPORT FOR IT YET. SO PLEASE DO NOT JUDGE CLARO BASED
        # TODO(steving)   SOLELY ON THIS PROGRAM ALONE.
        "HackingUtils": "//examples/claro_programs/advent_of_code_2023/utils:hacking_utils",
    },
)
