load("@io_bazel_rules_closure//closure:defs.bzl", "closure_java_template_library")

closure_java_template_library(
    name = "html_soy",
    srcs = glob(["*.soy"]),
    java_package = "com.claro.module_system.clarodocs.html_rendering",
    visibility = ["//src/java/com/claro/module_system/clarodocs/html_rendering:__subpackages__"],
)

java_library(
    name = "util",
    srcs = ["Util.java"],
    deps = [
        ":html_soy",
        "//:guava",
    ],
    visibility = ["//src/java/com/claro/module_system/clarodocs/html_rendering:__subpackages__"],
)