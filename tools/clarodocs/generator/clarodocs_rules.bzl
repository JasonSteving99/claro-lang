load("@aspect_bazel_lib//lib:expand_template.bzl", "expand_template")
load("@aspect_rules_js//js:defs.bzl", "js_library", "js_run_devserver")
load(
    "@claro-lang//src/java/com/claro:claro_build_rules_internal.bzl",
    _ClaroModuleInfo = "ClaroModuleInfo",
    _CLARO_STDLIB_MODULES = "CLARO_STDLIB_MODULES",
    _CLARO_OPTIONAL_STDLIB_MODULE_DEPS = "CLARO_OPTIONAL_STDLIB_MODULE_DEPS",
)
load("@claro-lang//tools/clarodocs:defs.bzl", "RUNTIME_DEPS")


def clarodocs(name, root, out = None):
    dep_graph_json_config = out if out != None else name + "_clarodocs_config.json"
    _clarodocs_rule(
        name = name + "_config",
        root = root + "_bin",
        out = dep_graph_json_config,
    )
    js_library(
        name = dep_graph_json_config + "_lib",
        srcs = [dep_graph_json_config]
    )
    expand_template(
        name = name + "_vite_config",
        template = "@claro-lang//tools/clarodocs/generator:vite-config.tmpl.js",
        data = [name + "_clarodocs_config.json"],
        substitutions = {
            "{{MODULE_DEP_GRAPH_CONFIG_JSON}}": dep_graph_json_config,
        },
        out = name + "-vite-config.js",
    )
    # The clarodocs site itself is hosted from `.../tools/clarodocs/` so in order for Vite to reference the generated
    # config at startup time we'll just move two levels back so that the current `native.package_name()` call works.
    generated_vite_config_relative_location = "../../" + native.package_name()
    # Fast developer round-trip under ibazel
    js_run_devserver(
        name = name,
        args = [".", "--config", generated_vite_config_relative_location + "/" + name + "-vite-config.js"],
        # TODO(steving) Figure out why js_run_devserver() isn't noticing that the dep_graph_json_config is changing as
        # TODO(steving)   the program's source code changes.
        data = RUNTIME_DEPS + [dep_graph_json_config + "_lib", name + "-vite-config.js"],
        tool = "@claro-lang//tools/clarodocs:vite",
    )


def _clarodocs_impl(ctx):
    args = ctx.actions.args()
    args.add("--root_name", ctx.attr.root.label.name[:-len("_bin")])
    for rootDep in ctx.attr.root[_ClaroModuleInfo].info.direct_deps.items():
        args.add("--root_dep", "{0}:{1}".format(rootDep[1], rootDep[0][_ClaroModuleInfo].info.unique_module_name))
    for module in ctx.attr.root[_ClaroModuleInfo].info.files.to_list():
        if module.extension == "claro_module":
            args.add("--module", module)
    for module in ctx.attr._stdlib_modules:
        args.add("--stdlib_module", module[_ClaroModuleInfo].info.path_to_claro_module_file)
    for module in ctx.attr._optional_stdlib_modules:
        args.add("--optional_stdlib_module", module[_ClaroModuleInfo].info.path_to_claro_module_file)
    print(ctx.attr.root[_ClaroModuleInfo].info.direct_deps)
    print(ctx.attr.root[_ClaroModuleInfo].info.direct_deps.items()[0][0][_ClaroModuleInfo].info.unique_module_name)

    args.add("--out", ctx.outputs.out.path)
    print(ctx.outputs.out)

    ctx.actions.run(
        inputs = ctx.attr.root[_ClaroModuleInfo].info.files.to_list() +
                [stdlib_dep[_ClaroModuleInfo].info.path_to_claro_module_file for stdlib_dep in ctx.attr._stdlib_modules],
        outputs = [ctx.outputs.out],
        arguments = [args],
        progress_message = "Generating ClaroDocs: " + ctx.outputs.out.path,
        executable = ctx.executable._clarodocs_generator_bin,
    )

    # TODO(steving) Update this message with something that actually shows how to view the new Node+React site.
    print("View auto-generated ClaroDocs by clicking the 'Local' link below:\n\t$ open " + ctx.outputs.out.path)


_clarodocs_rule = rule(
    implementation = _clarodocs_impl,
    attrs = {
        "root": attr.label(
            doc = "The root module for which ClaroDocs will be auto-generated for its entire transitive closure of dependencies.",
            providers = [_ClaroModuleInfo],
            mandatory = True,
        ),
        "out": attr.output(
            doc = "The output file to write the generated ClaroDocs JSON config to.",
            mandatory = True,
        ),
        "_stdlib_modules": attr.label_list(
            doc = "StdLib Modules that should be rendered in every ClaroDocs site since any types/procedures there will be used pervasively.",
            default = _CLARO_STDLIB_MODULES.values(),
        ),
        "_optional_stdlib_modules": attr.label_list(
            doc = "Optional StdLib Modules.",
            default = _CLARO_OPTIONAL_STDLIB_MODULE_DEPS.values(),
        ),
        "_clarodocs_generator_bin": attr.label(
            default = Label("@claro-lang//tools/clarodocs/generator:clarodocs_generator"),
            allow_files = True,
            executable = True,
            cfg = "host",
        ),
    },
)