load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    _ClaroModuleInfo = "ClaroModuleInfo",
    _CLARO_STDLIB_MODULES = "CLARO_STDLIB_MODULES",
    _CLARO_OPTIONAL_STDLIB_MODULE_DEPS = "CLARO_OPTIONAL_STDLIB_MODULE_DEPS",
)


def clarodocs(name, root, out = None):
    _clarodocs_rule(
        name = name,
        root = root + "_bin",
        out = out if out != None else "{0}_clarodocs.json".format(Label(root).name),
    )


def _clarodocs_impl(ctx):
    args = ctx.actions.args()
    for module in ctx.attr.root[_ClaroModuleInfo].info.files.to_list():
        if module.extension == "claro_module":
            args.add("--module", module)
    for module in ctx.attr._stdlib_modules:
        args.add("--stdlib_module", module[_ClaroModuleInfo].info.path_to_claro_module_file)
    for module in ctx.attr._optional_stdlib_modules:
        args.add("--optional_stdlib_module", module[_ClaroModuleInfo].info.path_to_claro_module_file)

    args.add("--out", ctx.outputs.out.path)

    ctx.actions.run(
        inputs = ctx.attr.root[_ClaroModuleInfo].info.files.to_list() +
                [stdlib_dep[_ClaroModuleInfo].info.path_to_claro_module_file for stdlib_dep in ctx.attr._stdlib_modules],
        outputs = [ctx.outputs.out],
        arguments = [args],
        progress_message = "Generating ClaroDocs: " + ctx.outputs.out.path,
        executable = ctx.executable._clarodocs_generator_bin,
    )

    # TODO(steving) Update this message with something that actually shows how to view the new Node+React site.
    print("View auto-generated ClaroDocs by running the following command:\n\t$ open " + ctx.outputs.out.path)


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
            default = Label("//tools/clarodocs/generator:clarodocs_generator"),
            allow_files = True,
            executable = True,
            cfg = "host",
        ),
    },
)