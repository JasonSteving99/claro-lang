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

def _claro_binary_impl(ctx): # TODO(steving) Rename to _claro_binary_impl() once finished w/ dev.
    # The user told me which .claro file is the main file, however, they may have also included it in the srcs list, so
    # filter it from the srcs. This way the main file is always guaranteed to be the first file in the list.
    srcs = [ctx.file.main_file] + [f for f in ctx.files.srcs if f != ctx.file.main_file]

    # By deriving the project package from the workspace name, this rule's able to ensure that generated Java sources
    # end up using unique Java packages so that it doesn't conflict with any downstream deps.
    project_package = ctx.workspace_name.replace('-', '.').replace('_', '.')

    # Declare an Action to execute the Claro compiler binary over the given srcs.
    # Constructing the args using ctx.actions.args() is Bazel's approach to performance optimization akin to Java's
    # use of StringBuilder rather than immediate String concatenations.
    args = ctx.actions.args()
    args.add("--java_source")
    classname = ctx.file.main_file.basename[:len(ctx.file.main_file.basename) - len(".claro")]
    args.add("--classname", classname)
    if not ctx.attr.debug:
        args.add("--silent")
    args.add("--package", project_package)
    for stdlib_src in ctx.files._stdlib_srcs:
        args.add("--src", stdlib_src)
    for src in srcs:
        args.add("--src", src)
    args.add("--output_file_path", ctx.outputs.compiled_java)

    ctx.actions.run(
        inputs = ctx.files._stdlib_srcs + srcs,
        outputs = [ctx.outputs.compiled_java],
        arguments = [args],
        progress_message = "Compiling Claro Program: " + ctx.outputs.compiled_java.short_path,
        executable = ctx.executable._claro_compiler,
    )


def claro_binary(name, **kwargs):
    main_file = kwargs['main_file']
    main_file_name = main_file[:len(main_file) - len(".claro")]
    _claro_binary(
        name = "{0}_compile".format(name),
        compiled_java = "{0}.java".format(main_file_name),
        **kwargs
    )

    native.java_binary(
        name = name,
        main_class = "claro.lang." + main_file_name,
        srcs = [":{0}_compile".format(name)],
        deps = CLARO_BUILTIN_JAVA_DEPS,
    )


_claro_binary = rule( # TODO(steving) Rename to claro_binary() once finished w/ dev.
    implementation = _claro_binary_impl,
    attrs = {
        "main_file": attr.label(
            mandatory = True,
            allow_single_file = [".claro"],
        ),
        "srcs": attr.label_list(allow_files = [".claro"]),
        "debug": attr.bool(default = False),
        "_claro_compiler": attr.label(
            default = Label("//src/java/com/claro:claro_compiler_binary"),
            allow_files = True,
            executable = True,
            cfg = "host",
        ),
        "_stdlib_srcs": attr.label_list(
            default = [Label(stdlib_file) for stdlib_file in CLARO_STDLIB_FILES],
            allow_files = [".claro_internal"],
        ),
        "compiled_java": attr.output(
            doc = "The .java source file codegen'd by the Claro compiler. This is an intermediate output produced by " +
                  "this rule, and manually inspecting it should not be necessary for most users.",
            mandatory = True,
        ),
    },
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
    if module_api_file:
        if not module_api_file.endswith(".claro_module_api"):
            fail("claro_library: Provided module_api_file must use .claro_module_api extension.")
        isModule = True
    else:
        isModule = False
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
        cmd = "$(JAVA) -jar $(location //src/java/com/claro:{0}_compiler_binary_deploy.jar) --java_source --silent={1} --classname={2} --package={3} --src $$(echo $(SRCS) | sed 's/ / --src /g') {4} > $(OUTS)".format(
            claro_compiler_name,
            "false" if debug else "true", # --silent
            java_name, # --classname
            # TODO(steving) Once this macro is migrated to being a full on Bazel "rule", swap this DEFAULT_PACKAGE w/
            # TODO(steving)   ctx.workspace_name instead.
            # TODO(steving)   All Bazel workspace names must be formatted as "Java-package-style" strings. This is a super convenient
            # TODO(steving)   way for this macro to automatically determine a "project_package" that should already be intended to be
            # TODO(steving)   globally (internet-wide) unique.
            # TODO(steving)   See: https://bazel.build/rules/lib/globals/workspace#parameters_3
            # ctx.workspace_name.replace('_', '.').replace('-', '.'), # --package
            DEFAULT_PACKAGE_PREFIX, # --package
            # Here, construct a totally unique name for this particular module. Since we're using Bazel, I have the
            # guarantee that this RULEDIR+target name is globally unique across the entire project.
            "--unique_module_name=$$(echo $(RULEDIR) | cut -c $$(($$(echo $(GENDIR) | wc -c ) - 1))- | tr '/' '$$')\\$$" + name if isModule else ""
        ),
        toolchains = ["@bazel_tools//tools/jdk:current_java_runtime"], # Gives the above cmd access to $(JAVA).
        tools = [
            "//src/java/com/claro:claro_compiler_binary_deploy.jar",
        ],
        outs = [java_name + ".java" if java_name else module_api_file[:-len("_api")]]
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
            "//:autovalue",
        ],
    )
