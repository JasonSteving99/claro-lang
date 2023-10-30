load("@jflex_rules//jflex:jflex.bzl", "jflex")
load("@jflex_rules//cup:cup.bzl", "cup")

# The entire intention of this .bzl file is to restrict external usage of Claro's internal build rule setup.
# Users should be unable to access certain config settings and should be required to come through the "front
# door" of the public claro_module() and claro_binary() rules.
visibility([
    "//src/java/com/claro/module_system/clarodocs/...",
    "//src/java/com/claro/compiler_backends/java_source/monomorphization/...",
    "//src/java/com/claro/stdlib/claro/...",
])

DEFAULT_CLARO_NAME = "claro"
DEFAULT_PACKAGE_PREFIX = "com.claro"

CLARO_STDLIB_FILES = [
    "//src/java/com/claro/stdlib/claro:builtin_functions.claro_internal",
]
# The names of these modules are going to be considered "reserved", so that language-wide users can become accustomed
# to these scopes being available (even if I decide to allow overriding stdlib dep modules in the future).
CLARO_STDLIB_MODULES = {
    "files": "//src/java/com/claro/stdlib/claro/files:files",
    "futures": "//src/java/com/claro/stdlib/claro/futures:futures",
    "lists": "//src/java/com/claro/stdlib/claro/lists:lists",
    "maps": "//src/java/com/claro/stdlib/claro/maps:maps",
    "sets": "//src/java/com/claro/stdlib/claro/sets:sets",
    "std": "//src/java/com/claro/stdlib/claro:std",
}
# Part of Claro's stdlib is going to be opt-in rather than bundled into your build by default. The intention here is to
# enable Claro to build smaller executables in cases where certain lesser used parts of the stdlib are not actually
# needed in a given Claro program.
CLARO_OPTIONAL_STDLIB_MODULE_DEPS = {
    "http": "//src/java/com/claro/stdlib/claro/http:http",
}
CLARO_BUILTIN_JAVA_DEPS = [
    "//:google-options",
    "//:guava",
    "//:gson",
    # This addresses unwanted missing StaticLoggerBinder warning logs from SLF4J. This shouldn't be necessary anymore
    # once Claro has proper logging support. See: https://www.slf4j.org/codes.html#StaticLoggerBinder
    "//:slf4j_nop",
    "//src/java/com/claro/stdlib",
    "//src/java/com/claro/intermediate_representation/types/impls:claro_type_implementation",
    "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls",
    "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/atoms",
    "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/collections:collections_impls",
    "//src/java/com/claro/intermediate_representation/types/impls/builtins_impls/futures:ClaroFuture",
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
    "//src/java/com/claro/runtime_utilities/flags:flags_util",
    "//src/java/com/claro/runtime_utilities/injector",
    "//src/java/com/claro/runtime_utilities/injector:key",
    "//src/java/com/claro/stdlib/userinput",
]


ClaroModuleInfo = provider(
    "Info needed to compile/link Claro Modules.",
    fields={
        "info": "A struct containing metadata about this module",
        "transitive_subgraph_dep_modules":
            "A depset representing transitive dep module dependencies for the sake of dep module monomorphization " +
            "having access to the .claro_module files at runtime.",
        "optional_stdlib_modules_used_in_transitive_closure":
            "A depset of all of the optional stdlib modules that have been used anywhere in this compilation unit's " +
            "transitive closure of deps. This will be used to signal to the top-level claro_binary() whether or not " +
            "extra teardown may be needed for some of these deps. E.g. Use of the `http` module would require main " +
            "to call $HttpUtil.shutdownOkHttpClient() at the end of the program run to avoid the program hanging.",
    })

def _invoke_claro_compiler_impl(ctx):
    is_module = ctx.file.module_api_file != None
    if is_module:
        srcs = [ctx.file.module_api_file] + ctx.files._stdlib_srcs + ctx.files.srcs
    else:
        # The user told me which .claro file is the main file, however, they may have also included it in the srcs list, so
        # filter it from the srcs. This way the main file is always guaranteed to be the first file in the list.
        srcs = ctx.files._stdlib_srcs + [ctx.file.main_file] + [f for f in ctx.files.srcs if f != ctx.file.main_file]
        classname = ctx.file.main_file.basename[:len(ctx.file.main_file.basename) - len(".claro")]

    # By deriving the project package from the workspace name, this rule's able to ensure that generated Java sources
    # end up using unique Java packages so that it doesn't conflict with any downstream deps.
    #     project_package = ctx.label.workspace_name.replace('-', '.').replace('_', '.')
    # TODO(steving) Figure out how to go back to using the workspace in the new Bzlmod way of the world. For now, it
    # TODO(steving)   doesn't work as ctx.workspace_name is always returning "_main" for some reason.
    project_package = "claro.lang"

    # Declare an Action to execute the Claro compiler binary over the given srcs.
    # Constructing the args using ctx.actions.args() is Bazel's approach to performance optimization akin to Java's
    # use of StringBuilder rather than immediate String concatenations.
    args = ctx.actions.args()
    args.add("--java_source")
    if is_module:
        args.add("--unique_module_name", ctx.attr.unique_module_name)
    else:
        args.add("--classname", classname)
    if not ctx.attr.debug:
        args.add("--silent")
    args.add("--package", project_package)
    for src in srcs:
        args.add("--src", src)
    dep_module_runfiles = []
    dep_module_targets_by_dep_name = {}

    def add_arg_for_dep_label_and_names(module_dep_label, concatenated_module_dep_names):
        added_dep_module_names = []
        # Add args for this direct dep.
        for module_dep_name in concatenated_module_dep_names.split('$'):
            dep_claro_module_file = module_dep_label[ClaroModuleInfo].info.path_to_claro_module_file
            dep_module_runfiles.append(dep_claro_module_file)
            dep_module_targets_by_dep_name[module_dep_name] = module_dep_label
            args.add("--dep", dep_claro_module_file, format = "{0}:%s".format(module_dep_name))
            added_dep_module_names.append(module_dep_name)
        # Add args for all the exported transitive deps exported by this dep.
        for transitive_dep_module_file in module_dep_label[DefaultInfo].files.to_list():
            args.add("--transitive_exported_dep_module", transitive_dep_module_file)
        return added_dep_module_names

    # Add all stdlib modules as dep module args.
    for module_dep_label, concatenated_module_dep_names in ctx.attr._stdlib_module_deps.items():
        added_dep_module_names = add_arg_for_dep_label_and_names(
            module_dep_label, concatenated_module_dep_names)
        for stdlib_dep_module_name in added_dep_module_names:
            # Signal to the compiler not to error on this dep being unused or not exported when it otw should've been.
            args.add("--stdlib_dep", stdlib_dep_module_name)
    for module_dep_label, concatenated_module_dep_names in ctx.attr.deps.items():
        add_arg_for_dep_label_and_names(module_dep_label, concatenated_module_dep_names)

    # Add all optional stdlib modules that the user explicitly placed a dep on. This will enable the user to explicitly
    # opt-in to parts of the stdlib that are only optionally bundled into your executable.
    for optional_stdlib_module_name in ctx.attr.optional_stdlib_deps:
        args.add("--optional_stdlib_dep", optional_stdlib_module_name)

    for resource, resourceName in ctx.attr.resources.items():
        args.add("--resource", resource.files.to_list()[0], format = "{0}:%s".format(resourceName))
    for export in ctx.attr.exports:
        args.add("--export", export)
    args.add("--output_file_path", ctx.outputs.compiler_out)

    # TODO(steving) Drop this once the "bootstrapping" version of the compiler also accepts this.
    if "bootstrapping" not in ctx.executable.claro_compiler.basename:
        # Add paths to all transitive .claro_module files for all modules in this compilation unit's subgraph.
        transitive_subgraph_dep_modules_depset = depset(
            direct = [dep[ClaroModuleInfo].info for dep in ctx.attr.deps] + [dep[ClaroModuleInfo].info for dep in ctx.attr._stdlib_module_deps.keys()],
            transitive = [dep[ClaroModuleInfo].transitive_subgraph_dep_modules for dep in ctx.attr.deps]
        )
        for transitive_subgraph_dep_module in transitive_subgraph_dep_modules_depset.to_list():
            args.add(
                "--dep_graph_claro_module_by_unique_name",
                "{0}:{1}".format(
                    transitive_subgraph_dep_module.unique_module_name,
                    transitive_subgraph_dep_module.path_to_claro_module_file.path))

    # Make sure to signal to the binary which (if any) optional stdlib modules have been used, because some (e.g. `http`)
    # may actually require some teardown in the main method.
    optional_stdlib_modules_used_in_transitive_closure = depset(
        direct = [opt_stdlib_module for opt_stdlib_module in ctx.attr.optional_stdlib_deps],
        transitive = [dep[ClaroModuleInfo].optional_stdlib_modules_used_in_transitive_closure for dep in ctx.attr.deps],
    )
    if not is_module:
        for used_optional_stdlib_module in optional_stdlib_modules_used_in_transitive_closure.to_list():
            args.add("--optional_stdlib_module_used_in_transitive_closure", used_optional_stdlib_module)

    ctx.actions.run(
        inputs = depset(
            direct = srcs,
            transitive = [dep.files for dep in ctx.attr._stdlib_module_deps.keys()] +
                         [dep[ClaroModuleInfo].info.files for dep in ctx.attr._stdlib_module_deps.keys()] +
                         [dep.files for dep in ctx.attr.deps.keys()] +
                         [dep[ClaroModuleInfo].info.files for dep in ctx.attr.deps]
        ),
        outputs = [ctx.outputs.compiler_out],
        arguments = [args],
        progress_message = "Compiling Claro Program: " + ctx.outputs.compiler_out.path,
        executable = ctx.executable.claro_compiler,
    )

    if is_module:
        # I actually want to immediately unpack the .claro_module and produce a .java file for the static java codegen of
        # this module. This is *solely* for the sake of this Bazel build being incremental. It's a bit strange for this rule
        # to have just packed this into the .claro_module and then immediately extract it, but for now, my thought process
        # is that this helps to ensure that this .java file is *actually* coming from this compiled module, and not from
        # some external Bazel shenanigans... TODO(steving) This decision should be revisited in the future.
        args = ctx.actions.args()
        args.add("--claro_module_file", ctx.outputs.compiler_out)
        args.add("--static_java_out", ctx.outputs.module_static_java_out)
        ctx.actions.run(
            inputs = [ctx.outputs.compiler_out],
            outputs = [ctx.outputs.module_static_java_out],
            arguments = [args],
            progress_message = "Extracting Module Static Java Codegen: " + ctx.outputs.module_static_java_out.short_path,
            executable = ctx.executable._module_deserialization_util,
        )

    return [
        DefaultInfo(
            files = depset(
                direct = [ctx.outputs.compiler_out],
                # Make sure to include the exported dep modules output files in this depset so that the
                # .claro_module_api files for transitive dep modules are available to the compiler at build time.
                transitive = [dep_module_targets_by_dep_name[export][DefaultInfo].files for export in ctx.attr.exports]
            ),
            runfiles = ctx.runfiles(files = dep_module_runfiles),
        ),
        ClaroModuleInfo(
            info = struct(
                unique_module_name = ctx.attr.unique_module_name,
                exports = [dep_module_targets_by_dep_name[export] for export in ctx.attr.exports],
                path_to_claro_module_file = ctx.outputs.compiler_out,
                files = depset(
                    direct = srcs + [ctx.outputs.compiler_out],
                    transitive = [dep[ClaroModuleInfo].info.files for dep in ctx.attr.deps]
                )
            ),
            transitive_subgraph_dep_modules = depset(
                direct = [dep[ClaroModuleInfo].info for dep in ctx.attr.deps],
                transitive = [dep[ClaroModuleInfo].transitive_subgraph_dep_modules for dep in ctx.attr.deps]
            ),
            optional_stdlib_modules_used_in_transitive_closure = optional_stdlib_modules_used_in_transitive_closure,
        )
    ]


def claro_binary(name, main_file, srcs = [], deps = {}, resources = {}, optional_stdlib_deps = [], debug = False, visibility = None):
    main_file_name = main_file[:len(main_file) - len(".claro")]

    # Add optional stdlib dep targets since the user doesn't actually "know" the explicit Bazel target that implements it.
    deps = dict(**deps) # Make a copy of the frozen deps dict.
    for optional_stdlib_dep in optional_stdlib_deps:
        deps[optional_stdlib_dep] = CLARO_OPTIONAL_STDLIB_MODULE_DEPS[optional_stdlib_dep]

    _invoke_claro_compiler(
        name = "{0}_bin".format(name),
        main_file = main_file,
        srcs = srcs,
        deps = _transpose_module_deps_dict(deps),
        resources = _transpose_module_deps_dict(
            resources,
            False #allowDuplicateValues
        ),
        optional_stdlib_deps = optional_stdlib_deps,
        compiler_out = "{0}.java".format(main_file_name),
        debug = debug,
        visibility = visibility,
    )
    native.java_binary(
        name = name,
        # TODO(steving) I need this package to be derived from the package computed in _invoke_claro_compiler().
        main_class = "claro.lang." + main_file_name,
        srcs = [":{0}.java".format(main_file_name)],
        deps = CLARO_BUILTIN_JAVA_DEPS +
            # Dict comprehension just to "uniquify" the dep targets. It's technically completely valid to reuse the same
            # dep more than once for different dep module impls in a claro_* rule.
            {"{0}_compiled_claro_module_java_lib".format(dep): "" for dep in deps.values()}.keys() +
            # Add the Stdlib Modules compiled java libs as default deps.
            ["{0}_compiled_claro_module_java_lib".format(Label(stdlib_mod)) for stdlib_mod in CLARO_STDLIB_MODULES.values()],
        resources = resources.values(),
    )

def claro_module(name, module_api_file, srcs, deps = {}, resources = {}, exports = [], optional_stdlib_deps = [], debug = False, **kwargs):
    _claro_module_internal(_invoke_claro_compiler, name, module_api_file, srcs, deps, resources, exports, exported_custom_java_deps = [], optional_stdlib_deps = optional_stdlib_deps, debug = debug, **kwargs)

def claro_module_internal(name, module_api_file, srcs, deps = {}, resources = {}, exports = [], exported_custom_java_deps = [], debug = False, **kwargs):
    _claro_module_internal(
        _invoke_claro_compiler_internal, name, module_api_file, srcs, deps, resources, exports, exported_custom_java_deps, optional_stdlib_deps = [], debug = debug, add_stdlib_deps = False, **kwargs)

# In order to avoid a circular dep back into the local build of the compiler, this compilation unit must be built using
# the "bootstrapping compiler" based on a prior precompiled release of the compiler from github.
def bootstrapped_claro_module(name, module_api_file, srcs, deps = {}, resources = {}, exports = [], optional_stdlib_deps = [], debug = False, **kwargs):
    _claro_module_internal(_invoke_claro_compiler, name, module_api_file, srcs, deps, resources, exports, exported_custom_java_deps = [], optional_stdlib_deps = optional_stdlib_deps, debug = debug,
        claro_compiler = "//:bootstrapping_claro_compiler_binary",
        override_claro_builtin_java_deps = ["//:bootstrapping_claro_builtin_java_deps_import"],
        **kwargs)

# In order to avoid a circular dep back into the local build of the compiler, this compilation unit must be built using
# the "bootstrapping compiler" based on a prior precompiled release of the compiler from github.
def bootstrapped_claro_module_internal(name, module_api_file, srcs, deps = {}, resources = {}, exports = [], exported_custom_java_deps = [], debug = False, **kwargs):
    _claro_module_internal(
        _invoke_claro_compiler_internal, name, module_api_file, srcs, deps, resources, exports, exported_custom_java_deps, optional_stdlib_deps = [], debug = debug, add_stdlib_deps = False,
        claro_compiler = "//:bootstrapping_claro_compiler_binary",
        override_claro_builtin_java_deps = ["//:bootstrapping_claro_builtin_java_deps_import"],
        **kwargs)

def _claro_module_internal(invoke_claro_compiler_rule, name, module_api_file, srcs, deps = {}, resources = {}, exports = [], exported_custom_java_deps = [], optional_stdlib_deps = [], debug = False, add_stdlib_deps = True, **kwargs):
    # Leveraging Bazel semantics to produce a unique module name from this target's Bazel package.
    # If this target is declared as //src/com/foo/bar:my_module, then the unique_module_name will be set to
    # 'src$com$foo$bar$my_module' which is guaranteed to be a name that's unique across this entire Bazel project.
    unique_module_name = native.package_name().replace('/', '$') + '$' + name

    # Validate that all `exports` strings must be present as dep module names.
    invalid_exports = [export for export in exports if export not in deps]
    if len(invalid_exports) > 0:
        fail("claro_module: The following declared exports are not valid dep modules.\n\t- {0}".format(
            "\n\t- ".join(invalid_exports),
        ))

    if add_stdlib_deps:
        # Add optional stdlib dep targets since the user doesn't actually "know" the explicit Bazel target that implements it.
        deps = dict(**deps) # Make a copy of the frozen deps dict.
        for optional_stdlib_dep in optional_stdlib_deps:
            deps[optional_stdlib_dep] = CLARO_OPTIONAL_STDLIB_MODULE_DEPS[optional_stdlib_dep]

    invoke_claro_compiler_rule(
        name = name,
        module_api_file = module_api_file,
        srcs = srcs,
        deps = _transpose_module_deps_dict(deps),
        resources = _transpose_module_deps_dict(
            resources,
            False #allowDuplicateValues
        ),
        exports = exports,
        optional_stdlib_deps = optional_stdlib_deps,
        unique_module_name = unique_module_name,
        compiler_out = "{0}.claro_module".format(name),
        module_static_java_out = "{0}.java".format(unique_module_name),
        debug = debug,
        # Default attrs like `visibility` will be set here so that Bazel defaults are honored.
        **{k:v for k,v in kwargs.items() if k != "override_claro_builtin_java_deps"}
    )
    native.java_library(
        name = "{0}_compiled_claro_module_java_lib".format(name),
        srcs = [":{0}.java".format(unique_module_name)],
        deps = (CLARO_BUILTIN_JAVA_DEPS if ("override_claro_builtin_java_deps" not in kwargs) else kwargs["override_claro_builtin_java_deps"]) +
            # Dict comprehension just to "uniquify" the dep targets. It's technically completely valid to reuse the same
            # dep more than once for different dep module impls in a claro_* rule.
            {"{0}_compiled_claro_module_java_lib".format(dep): "" for dep in deps.values()}.keys() +
            # Add the Stdlib Modules compiled java libs as default deps.
            (["{0}_compiled_claro_module_java_lib".format(stdlib_mod) for stdlib_mod in CLARO_STDLIB_MODULES.values()] if add_stdlib_deps else []) +
            # Add any custom Java deps that an internal optional stdlib module might need to add.
            exported_custom_java_deps,
        resources = resources.values(),
        exports = ["{0}_compiled_claro_module_java_lib".format(deps[export]) for export in exports] + \
                  exported_custom_java_deps,
        # Default attrs like `visibility` will be set here so that Bazel defaults are honored.
        **{k:v for k,v in kwargs.items() if k not in ["stdlib_srcs", "claro_compiler", "override_claro_builtin_java_deps"]}
    )

def _transpose_module_deps_dict(deps, allowDuplicateValues = True):
    res = {}
    for module_name, target in deps.items():
        if allowDuplicateValues and target in res:
            # Here, in order to allow a claro_*() rule to use the same impl target for multiple different dep modules,
            # I'll use the scheme of concatenating the module names using '$', which is an invalid identifier char in Claro.
            res[target] += "$" + module_name
        else:
            res[target] = module_name
    return res

INVOKE_CLARO_COMPILER_ATTRS = {
    "main_file": attr.label(
        doc = "The .claro file containing top-level statements to be executed as the program's main entrypoint.\n" +
              "module_api_file must not be set if this is set.",
        allow_single_file = [".claro"],
        default = None,
    ),
    "module_api_file": attr.label(
        doc = "The .claro_module_api file containing this Module's API.\n" +
              "main_file must not be set if this is set.",
        allow_single_file = [".claro_module_api"],
        default = None,
    ),
    "srcs": attr.label_list(allow_files = [".claro"]),
    "resources": attr.label_keyed_string_dict(
        doc = "Map of resource files that will be included in the compiled Jar file to be accessed at runtime.",
        allow_files = True,
    ),
    "deps": attr.label_keyed_string_dict(
        doc = "An optional set of Modules that this binary's sources directly depend on.",
        providers = [ClaroModuleInfo],
    ),
    "exports": attr.string_list(
        doc = "Exported transitive dep modules. Modules are listed here in order to ensure that consumers of this " +
              "module actually have access to the definitions of types defined in this module's deps."
    ),
    "optional_stdlib_deps": attr.string_list(
        doc = "Optional Stdlib Modules that you would like to place a dep on in order to make use of in this current " +
              "compilation unit. These modules are part of Claro's Stdlib, but are considered either too heavyweight, "+
              "or too infrequently needed to be bundled into every Claro executable by default. The available " +
              "optional stdlib modules are the following:\n" +
              '\n\t- '.join(CLARO_OPTIONAL_STDLIB_MODULE_DEPS.keys()),
        default = [],
    ),
    "debug": attr.bool(default = False),

    # Args below this point are intended primarily for internal use only.

    "unique_module_name": attr.string(),
    "claro_compiler": attr.label(
        default = Label("//src/java/com/claro:claro_compiler_binary"),
        allow_files = True,
        executable = True,
        cfg = "host",
    ),
    "_module_deserialization_util": attr.label(
        default = Label("//src/java/com/claro/compiler_backends/java_source/deserialization:claro_module_deserialization_util"),
        allow_files = True,
        executable = True,
        cfg = "host",
    ),
    # TODO(steving) Need to migrate away from _stdlib_srcs to _stdlib_module_deps once generic procedure exports are supported.
    "_stdlib_srcs": attr.label_list(
        default = [Label(stdlib_file) for stdlib_file in CLARO_STDLIB_FILES],
        allow_files = [".claro_internal"],
    ),
    "_stdlib_module_deps": attr.label_keyed_string_dict(
        default = _transpose_module_deps_dict(CLARO_STDLIB_MODULES),
        providers = [ClaroModuleInfo],
    ),
    "compiler_out": attr.output(
        doc = "The .java source file codegen'd by the Claro compiler. This is an intermediate output produced by " +
              "this rule, and manually inspecting it should not be necessary for most users.",
        mandatory = True,
    ),
    "module_static_java_out": attr.output(
        doc = "The .java source file that will contain the static java codegen extracted from the .claro_module " +
              "produced by this rule in order for dependent claro_binary() targets to access it directly for the " +
              "sake of incrementality.",
        mandatory = False,
    )
}

_invoke_claro_compiler = rule(
    implementation = _invoke_claro_compiler_impl,
    attrs = INVOKE_CLARO_COMPILER_ATTRS,
)

# Override the defaults for internal claro_modules so that we avoid circular deps when compiling the stdlib modules.
INVOKE_CLARO_COMPILER_INTERNAL_ATTRS = INVOKE_CLARO_COMPILER_ATTRS
INVOKE_CLARO_COMPILER_INTERNAL_ATTRS["srcs"] = attr.label_list(allow_files = [".claro", ".claro_internal"])
INVOKE_CLARO_COMPILER_INTERNAL_ATTRS["_stdlib_srcs"] = attr.label_list(default = [], allow_files = [".claro_internal"])
INVOKE_CLARO_COMPILER_INTERNAL_ATTRS["_stdlib_module_deps"] = attr.label_keyed_string_dict(default = {}, providers = [ClaroModuleInfo])
_invoke_claro_compiler_internal = rule(
    implementation = _invoke_claro_compiler_impl,
    attrs = INVOKE_CLARO_COMPILER_INTERNAL_ATTRS,
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
        resources = CLARO_STDLIB_FILES + CLARO_STDLIB_MODULES.values()
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
            ":scoped_identifier",
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
            "//src/java/com/claro/stdlib:module_util",
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

    native.java_library(
        name = "scoped_identifier",
        srcs = ["ScopedIdentifier.java"],
        deps = [
            "//:autovalue",
            "//src/java/com/claro/intermediate_representation/expressions/term:term_impls",
        ],
    )
