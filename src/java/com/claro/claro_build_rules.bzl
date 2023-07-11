load("@jflex_rules//jflex:jflex.bzl", "jflex")
load("@jflex_rules//cup:cup.bzl", "cup")

DEFAULT_CLARO_NAME = "claro"
DEFAULT_PACKAGE_PREFIX = "com.claro"

CLARO_STDLIB_FILES = [
    "//src/java/com/claro/stdlib/claro:builtin_functions.claro_internal",
    "//src/java/com/claro/stdlib/claro:builtin_types.claro_internal",
]
CLARO_BUILTIN_JAVA_DEPS = [
    "//:guava",
    "//:gson",
    "//:okhttp",
    "//:retrofit",
    "//src/java/com/claro/stdlib",
    "//src/java/com/claro/intermediate_representation/types/impls:claro_type_implementation",
    "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls",
    "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/atoms",
    "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/collections:collections_impls",
    "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/futures:ClaroFuture",
    "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/http:http_response",
    "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/procedures",
    "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/structs",
    "//src/java/com/claro/intermediate_representation/types/impls/user_defined_impls:user_defined_impls",
    "//src/java/com/claro/intermediate_representation/types:base_type",
    "//src/java/com/claro/intermediate_representation/types:concrete_type",
    "//src/java/com/claro/intermediate_representation/types:supports_mutable_variant",
    "//src/java/com/claro/intermediate_representation/types:type",
    "//src/java/com/claro/intermediate_representation/types:types",
    "//src/java/com/claro/intermediate_representation/types:type_provider",
    "//src/java/com/claro/runtime_utilities",
    "//src/java/com/claro/runtime_utilities/http",
    "//src/java/com/claro/runtime_utilities/http:http_server",
    "//src/java/com/claro/runtime_utilities/injector",
    "//src/java/com/claro/runtime_utilities/injector:key",
    "//src/java/com/claro/stdlib/userinput",
]

def claro_binary(name, srcs, java_name):
    native.java_binary(
        name = name,
        main_class = DEFAULT_PACKAGE_PREFIX + "." + java_name,
        srcs = srcs,
        deps = CLARO_BUILTIN_JAVA_DEPS
    )

# This macro produces a target that will allow you to build a claro_builtin_java_deps_deploy.jar that can be used by the
# CLI to compile Claro programs from source w/o using Bazel. This is intended for use in lightweight scripting scenarios
# and is not intended to be the primary form of building Claro programs. Bazel is very much the answer for large scale
# Claro programs (this will be particularly apparent once Claro's module/library system is built out).
def gen_claro_builtin_java_deps_jar():
    # I literally just have no idea how to create this JAR file using Bazel w/o a src-file.
    native.genrule(
        name = "dummy_java_file",
        cmd = "echo \"public class dummy {}\" > $(OUTS)",
        outs = ["dummy.java"]
    )
    native.java_binary(
        name = "claro_builtin_java_deps",
        srcs = [":dummy_java_file"],
        create_executable = False,
        deps = CLARO_BUILTIN_JAVA_DEPS,
        # Pack the stdlib into this JAR file just so that there's one fewer thing for users to download and keep in the
        # right place. This can always be changed later.
        resources = CLARO_STDLIB_FILES
    )

# TODO(steving) Once Modules are fully supported (including handling transitive deps) this should be renamed to
# TODO(steving)   claro_module and should always assume a .claro_module_api file is supplied. Then the claro_binary
# TODO(steving)   should be extended to also invoke the compiler over some main file and non-main files along with
# TODO(steving)   Module deps.
def claro_library(name, src, module_api_file = None, java_name = None, claro_compiler_name = DEFAULT_CLARO_NAME, debug = False):
    if module_api_file and java_name:
        fail("claro_library: java_name must *not* be set when compiling a Module as signalled by providing a module_api_file.")
    isModule = module_api_file != None
    if isModule:
        java_name = ""
    hasMultipleSrcs = str(type(src)) == "list"
    # TODO(steving) TESTING!!! I NEED TO CHECK FOR A MODULE API FILE AND IF SO REQUIRE java_name = None
    if hasMultipleSrcs:
        if not isModule and not java_name:
            fail("claro_library: java_name must be set when providing multiple srcs")
        javaNameMatchesASrc = False
        for filename in src:
            if not filename.endswith(".claro"):
                fail("claro_library: Provided srcs must use .claro extension.")
            if filename[:-6] == java_name:
                javaNameMatchesASrc = True
        if not isModule and not javaNameMatchesASrc:
            fail("claro_library: java_name must match one of the given srcs to indicate which one is the main file.")
    else:
        if not src.endswith(".claro"):
            fail("claro_library: Provided src must use .claro extension.")
        if not java_name:
            java_name = src[:-6]
    # Every Claro program comes prepackaged with a "stdlib". Achieve this by prepending default Claro src files.
    srcs = ([module_api_file] if isModule else []) + CLARO_STDLIB_FILES + (src if hasMultipleSrcs else [src])
    native.genrule(
        name = name,
        srcs = srcs,
        cmd = "$(JAVA) -jar $(location //src/java/com/claro:{0}_compiler_binary_deploy.jar) --java_source --silent={1} --classname={2} --package={3} --srcs=$$(echo $(SRCS) | tr ' ' ',') {4} > $(OUTS)".format(
            claro_compiler_name,
            "false" if debug else "true", # --silent
            java_name, # --classname
            DEFAULT_PACKAGE_PREFIX, # --package
            # Here, construct a totally unique name for this particular module. Since we're using Bazel, I have the
            # guarantee that this RULEDIR+target name is globally unique across the entire project.
            "--unique_module_name=$$(echo $(RULEDIR) | cut -c $$(($$(echo $(GENDIR) | wc -c ) - 1))- | tr '/' '$$')\$$" + name if isModule else ""
        ),
        toolchains = ["@bazel_tools//tools/jdk:current_java_runtime"], # Gives the above cmd access to $(JAVA).
        tools = [
            "//src/java/com/claro:claro_compiler_binary_deploy.jar",
        ],
        outs = [(java_name if java_name else module_api_file[:-len(".claro_module_api")]) + ".java"]
    )

def gen_claro_compiler(name = DEFAULT_CLARO_NAME):
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
            "//src/java/com/claro/intermediate_representation/expressions:unwrap_user_defined_type_expr_impl",
            "//src/java/com/claro/intermediate_representation/expressions/bool:bool_expr",
            "//src/java/com/claro/intermediate_representation/expressions/bool:bool_expr_impls",
            "//src/java/com/claro/intermediate_representation/expressions/numeric:numeric_expr_impls",
            "//src/java/com/claro/intermediate_representation/expressions/procedures/functions",
            "//src/java/com/claro/intermediate_representation/expressions/term:term",
            "//src/java/com/claro/intermediate_representation/expressions/term:term_impls",
            "//src/java/com/claro/intermediate_representation/statements:stmt",
            "//src/java/com/claro/intermediate_representation/statements:stmt_impls",
            "//src/java/com/claro/intermediate_representation/statements:stmt_list_node",
            "//src/java/com/claro/intermediate_representation/statements/contracts",
            "//src/java/com/claro/intermediate_representation/statements/user_defined_type_def_stmts",
            "//src/java/com/claro/intermediate_representation/types:base_type",
            "//src/java/com/claro/intermediate_representation/types:claro_type_exception",
            "//src/java/com/claro/intermediate_representation/types:supports_mutable_variant",
            "//src/java/com/claro/intermediate_representation/types:type_provider",
            "//src/java/com/claro/intermediate_representation/types:type",
            "//src/java/com/claro/intermediate_representation/types:types",
            "//src/java/com/claro/runtime_utilities/injector:injected_key",
            "//src/java/com/claro/runtime_utilities/injector:injected_key_identifier",
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
        symbols = "Tokens",
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
