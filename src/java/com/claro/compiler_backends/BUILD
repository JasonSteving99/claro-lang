package(
    default_visibility = [
        "//src/java/com/claro/compiler_backends:__subpackages__",
    ]
)

load("//src/java/com/claro:claro_build_rules.bzl", "DEFAULT_CLARO_NAME")

java_library(
    name = "compiler_backend",
    srcs = glob(["*.java"]),
    deps = [
      "//src/java/com/claro:" + DEFAULT_CLARO_NAME + "_java_parser",
    ]
)
