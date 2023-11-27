# This package only exists to convert serialized .claro_module files to JSON to make for easier consumption by JS-based
# tools like the hypothetical auto-docs generator.

java_binary(
    name = "serialized_claro_module_json_converter",
    srcs = ["SerializedClaroModuleJsonConverter.java"],
    main_class = "com.claro.module_system.module_serialization.json.SerializedClaroModuleJsonConverter",
    deps = [
        "//:protobuf_java_util",
        "//src/java/com/claro/module_system/module_serialization/proto:serialized_claro_module_java_proto",
    ],
)