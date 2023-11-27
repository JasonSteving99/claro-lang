load("//src/java/com/claro:claro_build_rules_internal.bzl", "bootstrapped_claro_module_internal", "claro_binary")


bootstrapped_claro_module_internal(
    name = "http",
    module_api_file = "http.claro_module_api",
    srcs = ["http.claro_internal"],
    exported_custom_java_deps = [
        "//:okhttp",
        "//:retrofit",
        "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/http:http_response",
        "//src/java/com/claro/runtime_utilities/http",
        "//src/java/com/claro/runtime_utilities/http:http_server",
    ],
    visibility = ["//visibility:public"],
)