load("@jflex_rules//jflex:jflex.bzl", "jflex")
load("@jflex_rules//cup:cup.bzl", "cup")
load("@io_bazel_rules_docker//java:image.bzl", "java_image")

DEFAULT_CALCULATOR_NAME = "calculator"
DEFAULT_PACKAGE_PREFIX = "com.claro.examples.calculator_example"

# Leaving this named as *_binary literally just because then IntelliJ is nice
# to me and will automatically pick up the target as executable.
def java_calculator_binary(name, srcs, java_name = "CompiledCalculator"):
    java_image(
        name = name,
        main_class = DEFAULT_PACKAGE_PREFIX + "." + java_name,
        # Put these runfiles into their own layer.
        layers = srcs,
        srcs = srcs,
    )

def java_calculator_library(name, src, java_name = "CompiledCalculator", calculator_compiler_name = DEFAULT_CALCULATOR_NAME):
    native.genrule(
        name = name,
        srcs = [src],
        cmd = "$(JAVA) -jar $(location :{0}_compiler_binary_deploy.jar) --silent --classname={1} --package={2} < $< > $(OUTS)".format(
            calculator_compiler_name,
            java_name,
            DEFAULT_PACKAGE_PREFIX,
        ),
        toolchains = ["@bazel_tools//tools/jdk:current_java_runtime"], # Gives the above cmd access to $(JAVA).
        tools = ["//src/java/com/claro/examples/calculator_example:calculator_compiler_binary_deploy.jar"],
        outs = [java_name + ".java"]
    )

def gen_calculator_compiler(name = DEFAULT_CALCULATOR_NAME):
    java_image(
        name = name + "_compiler_image",
        main_class = DEFAULT_PACKAGE_PREFIX + ".CalculatorCompilerMain",
        runtime_deps = [":" + name + "_compiler_binary"]
    )
    native.java_binary(
        name = name + "_compiler_binary",
        main_class = DEFAULT_PACKAGE_PREFIX + ".CalculatorCompilerMain",
        runtime_deps = [":" + name + "_compiler_main"],
    )

    native.java_library(
        name = name + "_compiler_main",
        srcs = [
            "CalculatorCompilerMain.java"
        ],
        deps = [
            ":" + name + "_java_parser",
        ],
    )

    native.java_library(
        name = name + "_java_parser",
        srcs = [
            "CalculatorParserException.java",
            ":" + name + "_gen_lexer",
            ":" + name + "_gen_parser",
        ],
        deps = [
            "//src/java/com/claro/examples/calculator_example/intermediate_representation:integer_term",
            "//src/java/com/claro/examples/calculator_example/intermediate_representation:float_term",
            "//src/java/com/claro/examples/calculator_example/intermediate_representation:string_term",
            "//src/java/com/claro/examples/calculator_example/intermediate_representation:node",
            "//:apache_commons_text",
            "@jflex_rules//third_party/cup",  # the runtime would be sufficient
        ],
    )

    cup(
        name = name + "_gen_parser",
        src = "calculator.cup",
        parser = "CalculatorParser",
        symbols = "Calc",
    )

    jflex(
        name = name + "_gen_lexer",
        srcs = ["calculator.flex"],
        outputs = ["CalculatorLexer.java"],
    )
