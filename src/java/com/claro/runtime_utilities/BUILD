package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/claro_programs:__pkg__",
        "//src/java/com/claro/intermediate_representation/expressions:__pkg__",
    ]
)

java_library(
    name = "runtime_utilities",
    srcs = glob(["*.java"]),
    deps = [
        "//src/java/com/claro/intermediate_representation/types:concrete_type",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/intermediate_representation/types/builtins_impls:builtins_impls",
    ],
)
