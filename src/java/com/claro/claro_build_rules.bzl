load("@jflex_rules//jflex:jflex.bzl", "jflex")
load("@jflex_rules//cup:cup.bzl", "cup")
load("@io_bazel_rules_docker//java:image.bzl", "java_image")

DEFAULT_CLARO_NAME = "claro"
DEFAULT_PACKAGE_PREFIX = "com.claro"

def claro_binary(name, srcs, java_name):
    native.java_binary(
        name = name,
        main_class = DEFAULT_PACKAGE_PREFIX + "." + java_name,
        srcs = srcs,
        deps = [
            "//:autovalue",
            "//:guava",
            "//:lombok",
            "//src/java/com/claro/stdlib",
            "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/collections:collections_impls",
            "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/futures:ClaroFuture",
            "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/procedures",
            "//src/java/com/claro/intermediate_representation/types/impls/user_defined_impls:user_defined_impls",
            "//src/java/com/claro/intermediate_representation/types:base_type",
            "//src/java/com/claro/intermediate_representation/types:concrete_type",
            "//src/java/com/claro/intermediate_representation/types:type",
            "//src/java/com/claro/intermediate_representation/types:types",
            "//src/java/com/claro/runtime_utilities",
            "//src/java/com/claro/runtime_utilities/injector",
            "//src/java/com/claro/runtime_utilities/injector:key",
            "//src/java/com/claro/stdlib/userinput",
        ]
    )

def claro_image(name, srcs, java_name = "CompiledClaroProgram"):
    java_image(
        name = name,
        main_class = DEFAULT_PACKAGE_PREFIX + "." + java_name,
        # Put these runfiles into their own layer.
        layers = srcs,
        srcs = srcs,
    )

def claro_library(name, src, java_name = None, claro_compiler_name = DEFAULT_CLARO_NAME, debug = False):
    if not src.endswith(".claro"):
        fail("claro_library: Provided src must use .claro extension.")
    if not java_name:
        java_name = src[:-6]
    native.genrule(
        name = name,
        srcs = [src],
        cmd = "$(JAVA) -jar $(location //src/java/com/claro:{0}_compiler_binary_deploy.jar) --java_source --silent={1} --classname={2} --package={3} < $< > $(OUTS)".format(
            claro_compiler_name,
            "false" if debug else "true", # --silent
            java_name, # --classname
            DEFAULT_PACKAGE_PREFIX, # --package
        ),
        toolchains = ["@bazel_tools//tools/jdk:current_java_runtime"], # Gives the above cmd access to $(JAVA).
        tools = [
            "//src/java/com/claro:claro_compiler_binary_deploy.jar",
        ],
        outs = [java_name + ".java"]
    )

def gen_claro_compiler(name = DEFAULT_CLARO_NAME):
    java_image(
        name = name + "_compiler_image",
        main_class = DEFAULT_PACKAGE_PREFIX + ".ClaroCompilerMain",
        runtime_deps = [":" + name + "_compiler_binary"]
    )
    native.java_binary(
        name = name + "_compiler_binary",
        main_class = DEFAULT_PACKAGE_PREFIX + ".ClaroCompilerMain",
        runtime_deps = [":" + name + "_compiler_main"],
    )

    native.java_library(
        name = name + "_compiler_main",
        srcs = [
            "ClaroCompilerMain.java"
        ],
        deps = [
            ":" + name + "_java_parser",
            "//src/java/com/claro/compiler_backends/interpreted:interpreter",
            "//src/java/com/claro/compiler_backends/java_source:java_source",
            "//src/java/com/claro/compiler_backends/repl:repl",
        ],
    )

    native.java_library(
        name = name + "_java_parser",
        srcs = [
            ":" + name + "_gen_lexer",
            ":" + name + "_gen_parser",
        ],
        deps = [
            ":claro_parser_exception",
            ":lexed_value",
            "//src/java/com/claro/compiler_backends/interpreted:scoped_heap",
            "//src/java/com/claro/intermediate_representation:node",
            "//src/java/com/claro/intermediate_representation:program_node",
            "//src/java/com/claro/intermediate_representation/expressions:expr",
            "//src/java/com/claro/intermediate_representation/expressions:expr_impls",
            "//src/java/com/claro/intermediate_representation/expressions:lambda_expr_impl",
            "//src/java/com/claro/intermediate_representation/expressions/bool:bool_expr",
            "//src/java/com/claro/intermediate_representation/expressions/bool:bool_expr_impls",
            "//src/java/com/claro/intermediate_representation/expressions/numeric:numeric_expr_impls",
            "//src/java/com/claro/intermediate_representation/expressions/procedures/functions",
            "//src/java/com/claro/intermediate_representation/expressions/procedures/methods",
            "//src/java/com/claro/intermediate_representation/expressions/term:term",
            "//src/java/com/claro/intermediate_representation/expressions/term:term_impls",
            "//src/java/com/claro/intermediate_representation/statements:stmt",
            "//src/java/com/claro/intermediate_representation/statements:stmt_impls",
            "//src/java/com/claro/intermediate_representation/statements:stmt_list_node",
            "//src/java/com/claro/intermediate_representation/statements/contracts",
            "//src/java/com/claro/intermediate_representation/statements/user_defined_type_def_stmts",
            "//src/java/com/claro/intermediate_representation/types:type_provider",
            "//src/java/com/claro/intermediate_representation/types:type",
            "//src/java/com/claro/intermediate_representation/types:types",
            "//src/java/com/claro/runtime_utilities/injector:injected_key",
            "//:apache_commons_text",
            "//:guava",
            "//:lombok",
            "@jflex_rules//third_party/cup",  # the runtime would be sufficient
        ],
    )

    native.java_library(
        name = "claro_parser_exception",
        srcs = ["ClaroParserException.java"],
    )

    cup(
        name = name + "_gen_parser",
        src = "ClaroParser.cup",
        parser = "ClaroParser",
        symbols = "Calc",
        # TODO(steving) This is getting dangerous. This arose from a reduce/reduce conflict between identifier and user
        # TODO(steving) defined type name reference (which is also just an identifier). Hopefully eliminate the conflict.
        expected_conflicts = 1,
    )

    jflex(
        name = name + "_gen_lexer",
        srcs = ["ClaroLexer.flex"],
        outputs = ["ClaroLexer.java"],
    )

    native.java_library(
        name = "lexed_value",
        srcs = ["LexedValue.java"],
        deps = [
            "//:lombok",
        ],
    )
