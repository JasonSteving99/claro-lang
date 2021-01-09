# Copyright 2018 Google LLC.
# SPDX-License-Identifier: Apache-2.0

package(default_visibility = ["//visibility:public"])

load("@jflex_rules//jflex:jflex.bzl", "jflex")
load("@jflex_rules//cup:cup.bzl", "cup")

java_binary(
    name = "calculator_binary",
    main_class = "com.claro.examples.calculator_example.CalculatorMain",
    runtime_deps = [":calculator_main"],
)

java_library(
    name = "calculator_main",
    srcs = [
        "CalculatorMain.java",
    ],
    deps = [
        ":calculator",
    ],
)

java_library(
    name = "calculator",
    srcs = [
        "CalculatorParserException.java",
        ":gen_lexer",
        ":gen_parser",
    ],
    deps = [
        "@jflex_rules//third_party/cup",  # the runtime would be sufficient
    ],
)

cup(
    name = "gen_parser",
    src = "calculator.cup",
    parser = "CalculatorParser",
    symbols = "Calc",
)

jflex(
    name = "gen_lexer",
    srcs = ["calculator.flex"],
    outputs = ["CalculatorLexer.java"],
)
