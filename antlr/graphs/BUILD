load("@rules_antlr//antlr:antlr4.bzl", "antlr")
load("//antlr/graphs:graph_pngs.bzl", "render_graphs")

antlr(
    name = "parser_gen",
    srcs = ["GraphsLexer.g4", "GraphsParser.g4"],
    package = "claro.lang",
)

java_library(
    name = "antlr_parser",
    srcs = [":parser_gen"],
    deps = ["@antlr4_runtime//jar"],
)

java_binary(
    name = "test_parser",
    srcs = ["TestGraphsParser.java", "TestGraphsParserCLIOptions.java"],
    main_class = "claro.lang.TestGraphsParser",
    deps = [
        "//:google-options",
        ":antlr_parser",
        "@antlr4_runtime//jar",
    ],
    visibility = ["//visibility:public"],
)