package(
    default_visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/claro_programs:__subpackages__",
        "//src/java/com/claro/intermediate_representation/expressions/procedures/functions:__pkg__",
        "//src/java/com/claro/intermediate_representation/statements:__pkg__",
        "//src/java/com/claro/intermediate_representation/types:__pkg__",
    ]
)

java_library(
    name = "injected_key",
    srcs = ["InjectedKey.java"],
    # This is an internal only type, not for consumption by the generated JavaSource output.
    visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/intermediate_representation/statements:__pkg__",
    ],
    deps = [
        "//:lombok",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
    ],
)

java_library(
    name = "injected_key_identifier",
    srcs = ["InjectedKeyIdentifier.java"],
    # This is an internal only type, not for consumption by the generated JavaSource output.
    visibility = [
        "//src/java/com/claro:__pkg__",
        "//src/java/com/claro/intermediate_representation/statements:__pkg__",
    ],
    deps = [
        ":injected_key",
        "//:lombok",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
        "//src/java/com/claro/intermediate_representation/expressions/term:term_impls",
    ],
)

java_library(
    name = "key",
    srcs = ["Key.java"],
    deps = [
        "//:lombok",
        "//src/java/com/claro/intermediate_representation/types:type",
    ],
)

java_library(
    name = "injector",
    srcs = ["Injector.java"],
    deps = [
        ":key",
        "//:guava",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/statements:stmt",
    ],
)
