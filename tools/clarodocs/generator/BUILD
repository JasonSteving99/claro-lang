load("//tools/clarodocs/generator:clarodocs_rules.bzl", "clarodocs")

exports_files(["vite-config.tmpl.js"])

load("@aspect_rules_js//js:defs.bzl", "js_library", "js_run_devserver")
js_library(
    name = "test_setup_sh",
    srcs = ["test_setup.sh"],
    visibility = ["//visibility:public"],
)

java_binary(
    name = "clarodocs_generator",
    main_class = "com.claro.tools.clarodocs.generator.ClaroDocsGenerator",
    srcs = [
        "ClaroDocsCLIOptions.java",
        "ClaroDocsGenerator.java",
    ],
    deps = [
        "//:google-options",
        "//:guava",
        "//src/java/com/claro/compiler_backends/java_source:java_source_compiler_backend_cli_options",
        "//src/java/com/claro/module_system/module_serialization/proto:serialized_claro_module_java_proto",
    ],
    visibility = ["//visibility:public"],
)

clarodocs(
    name = "test",
    root = "//examples/claro_programs/demo_server/buggy_buggies:buggy_buggies_http_server",
)