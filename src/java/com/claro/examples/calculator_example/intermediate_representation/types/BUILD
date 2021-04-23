package(
    default_visibility = [
        "//src/java/com/claro/examples/calculator_example/intermediate_representation:__pkg__",
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:__pkg__",
        "//src/java/com/claro/examples/calculator_example:__subpackages__",
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
        "//src/java/com/claro/examples/calculator_example/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/examples/calculator_example/intermediate_representation:expr",
        "//:autovalue",
        "//:guava",
    ],
)

java_library(
    name = "type",
    srcs = ["Type.java"],
    deps = [
        ":base_type",
        "//:guava",
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