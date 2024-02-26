load("@rules_antlr//antlr:antlr4.bzl", "antlr")

antlr(
    name = "lexer_gen",
    srcs = ["HelloLexer.g4"],
    package = "hello.world",
)
antlr(
    name = "parser_gen",
    srcs = ["HelloParser.g4"],
    package = "hello.world",
    imports = ["gen/HelloLexer.tokens"],
)

java_library(
    name = "antlr_lexer",
    srcs = [":lexer_gen"],
    deps = ["@antlr4_runtime//jar"],
)
java_library(
    name = "antlr_parser",
    srcs = [":parser_gen"],
    deps = ["@antlr4_runtime//jar"],
)

java_binary(
    name = "test_parser",
    srcs = ["TestParser.java"],
    main_class = "hello.world.TestParser",
    deps = [
        ":antlr_lexer",
        ":antlr_parser",
        "@antlr4_runtime//jar",
    ],
    resources = [
        "test.hello",
    ],
)