package(default_visibility = ["//src/java/com/claro/compiler_backends/repl:__pkg__"])

java_library(
    name = "repl_terminal",
    srcs = ["ReplTerminal.java"],
    deps = [
        "//:lanterna",
        "@bazel_tools//tools/java/runfiles",
    ],
    data = [
        "//:CLARO_VERSION.txt",
    ],
)