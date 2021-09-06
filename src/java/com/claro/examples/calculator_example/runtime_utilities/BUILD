package(
    default_visibility = [
        "//src/java/com/claro/examples/calculator_example:__pkg__",
        "//src/java/com/claro/examples/calculator_example/calculator_programs:__pkg__",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation:__pkg__",
    ]
)

java_library(
    name = "runtime_utilities",
    srcs = glob(["*.java"]),
    deps = [
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types:concrete_type",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types:type",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation/types/builtins_impls:builtins_impls",
    ],
)
