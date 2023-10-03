
java_library(
    name = "type_html",
    srcs = ["TypeHtml.java"],
    deps = [
        "//:guava",
        "//src/java/com/claro/intermediate_representation/types:type",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/intermediate_representation/types:supports_mutable_variant",
        "//src/java/com/claro/module_system/clarodocs/html_rendering:util",
    ],
    # Types are gonna get used all over.
    visibility = ["//src/java/com/claro/module_system/clarodocs/html_rendering:__subpackages__"],
)