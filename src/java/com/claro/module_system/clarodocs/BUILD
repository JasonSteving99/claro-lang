load("//src/java/com/claro/module_system/clarodocs:clarodocs_rules.bzl", "clarodocs")

java_binary(
    name = "clarodocs_generator",
    main_class = "com.claro.module_system.clarodocs.ClaroDocsGenerator",
    srcs = [
        "ClaroDocsCLIOptions.java",
        "ClaroDocsGenerator.java",
    ],
    deps = [
        "//:google-options",
        "//:guava",
        "//src/java/com/claro/intermediate_representation/types:types",
        "//src/java/com/claro/module_system/clarodocs/html_rendering/aliases",
        "//src/java/com/claro/module_system/clarodocs/html_rendering/contracts",
        "//src/java/com/claro/module_system/clarodocs/html_rendering/homepage:homepage_html",
        "//src/java/com/claro/module_system/clarodocs/html_rendering/procedures:procedure_html",
        "//src/java/com/claro/module_system/clarodocs/html_rendering/typedefs:type_html",
        "//src/java/com/claro/module_system/module_serialization/proto:serialized_claro_module_java_proto",
    ],
    visibility = ["//visibility:public"],
)