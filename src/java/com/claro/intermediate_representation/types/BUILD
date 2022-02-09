package(
    default_visibility = [
        "//src/java/com/claro/intermediate_representation:__pkg__",
        "//src/java/com/claro/intermediate_representation/types/builtins_impls:__pkg__",
        "//src/java/com/claro/compiler_backends/interpreted:__pkg__",
        "//src/java/com/claro:__subpackages__",
    ]
)

java_library(
    name = "concrete_type",
    srcs = ["ConcreteType.java"],
    deps = [
        ":base_type",
        ":type",
        "//:autovalue",
        "//:guava",
    ],
)

java_library(
    name = "concrete_types",
    srcs = ["ConcreteTypes.java"],
    deps = [
        ":base_type",
        ":concrete_type",
        ":type",
    ],
)

java_library(
    name = "parameterized_type",
    srcs = ["ParameterizedType.java"],
    deps = [
        ":base_type",
        ":type",
        "//:autovalue",
        "//:guava",
    ],
)

java_library(
    name = "types",
    srcs = ["Types.java"],
    deps = [
        ":base_type",
        ":concrete_type",
        ":parameterized_type",
        ":type",
        ":type_provider",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//:autovalue",
        "//:guava",
        "//:lombok",
    ],
)

java_library(
    name = "type",
    srcs = ["Type.java"],
    deps = [
        ":base_type",
        "//:guava",
        "//src/java/com/claro:claro_parser_exception",
    ],
)

java_library(
    name = "type_provider",
    srcs = ["TypeProvider.java"],
    deps = [
        ":type",
        "//:guava",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
    ],
)

java_library(
    name = "base_type",
    srcs = ["BaseType.java"],
)

java_library(
    name = "claro_type_exception",
    srcs = ["ClaroTypeException.java"],
    deps = [
        ":base_type",
        ":type",
        "//:guava",
    ],
)