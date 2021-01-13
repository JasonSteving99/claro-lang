# Copyright 2018 Google LLC.
# SPDX-License-Identifier: Apache-2.0

package(default_visibility = ["//visibility:public"])

load("@jflex_rules//jflex:jflex.bzl", "jflex")
load("@jflex_rules//cup:cup.bzl", "cup")

java_binary(
    name = "compiled_calculator_binary",
    main_class = "com.claro.examples.calculator_example.CompiledCalculator",
    srcs = [":compiled_calculator"],
)

genrule(
    name = "compiled_calculator",
    srcs = ["//src/java/com/claro/examples/calculator_example/calculator_programs:first.calculator"],
    cmd = "$(JAVA) -jar $(location :calculator_compiler_binary_deploy.jar) --silent < $< > $(OUTS)",
    toolchains = ["@bazel_tools//tools/jdk:current_java_runtime"],
    tools = [":calculator_compiler_binary_deploy.jar"],
    outs = ["CompiledCalculator.java"]
)

java_binary(
    name = "calculator_compiler_binary",
    main_class = "com.claro.examples.calculator_example.CalculatorCompilerMain",
    runtime_deps = [":calculator_compiler_main"],
)

java_library(
    name = "calculator_compiler_main",
    srcs = [
        "CalculatorCompilerMain.java"
    ],
    deps = [
        ":calculator_parser",
    ],
)

java_library(
    name = "calculator_parser",
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
