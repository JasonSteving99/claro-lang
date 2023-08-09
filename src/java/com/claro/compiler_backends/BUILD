package(
    default_visibility = [
        "//src/java/com/claro/compiler_backends:__subpackages__",
        "//src/java/com/claro/repl_site/server/handlers:__subpackages__",
        "//src/java/com/claro/stdlib:__pkg__",
    ]
)

java_library(
    name = "compiler_backend",
    srcs = glob(["*.java"], exclude = ["ParserUtil.java"]),
    deps = [
        ":parser_util",
    ]
)

java_library(
    name = "parser_util",
    srcs = glob(["ParserUtil.java"]),
    deps = [
      "//src/java/com/claro:claro_java_parser",
    ]
)
