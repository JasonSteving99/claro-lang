package(
    default_visibility = [
        "//visibility:public",
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
        "//src/java/com/claro/module_system/module_serialization/proto/claro_types:claro_types_java_proto",
    ],
)

java_library(
    name = "concrete_types",
    srcs = ["ConcreteTypes.java"],
    deps = [
        ":base_type",
        ":concrete_type",
        ":type",
        "//src/java/com/claro/module_system/module_serialization/proto/claro_types:claro_types_java_proto",
    ],
)

java_library(
    name = "supports_mutable_variant",
    srcs = ["SupportsMutableVariant.java"],
    deps = [
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
        ":concrete_types",
        ":parameterized_type",
        ":supports_mutable_variant",
        ":type",
        ":type_provider",
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
        "//src/java/com/claro/intermediate_representation/expressions:expr",
        "//src/java/com/claro/intermediate_representation/statements:stmt",
        "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
        "//src/java/com/claro/module_system/module_serialization/proto/claro_types:claro_types_java_proto",
        "//src/java/com/claro/runtime_utilities/injector:key",
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
        "//src/java/com/claro:claro_parser_exception",
        "//src/java/com/claro/module_system/module_serialization/proto/claro_types:claro_types_java_proto",
    ],
)

java_library(
    name = "type_provider",
    srcs = ["TypeProvider.java"],
    deps = [
        ":base_type",
        ":type",
        "//:autovalue",
        "//:guava",
        "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
    ],
)

java_library(
    name = "base_type",
    srcs = ["BaseType.java"],
    deps = [
        "//:guava",
    ]
)

java_library(
    name = "claro_type_exception",
    srcs = ["ClaroTypeException.java"],
    deps = [
        ":base_type",
        ":type",
        "//:guava",
        "//src/java/com/claro/runtime_utilities/injector:key",
    ],
)