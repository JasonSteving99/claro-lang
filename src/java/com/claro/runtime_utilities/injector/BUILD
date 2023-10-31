package(
    default_visibility = [
        "//visibility:public",
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
        "//:autovalue",
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
        "//:autovalue",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:type_provider",
        "//src/java/com/claro/intermediate_representation/expressions/term:term_impls",
    ],
)

java_library(
    name = "key",
    srcs = ["Key.java"],
    deps = [
        "//:autovalue",
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
