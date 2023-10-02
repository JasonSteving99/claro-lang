load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    _ClaroModuleInfo = "ClaroModuleInfo",
)


def clarodocs(name, root, out = None):
    _clarodocs_rule(
        name = name,
        root = root + "_bin",
        out = out if out != None else "{0}_clarodocs.html".format(Label(root).name),
    )


def _clarodocs_impl(ctx):
    args = ctx.actions.args()
    for module in ctx.attr.root[_ClaroModuleInfo].info.files.to_list():
        if module.extension == "claro_module":
            args.add("--module", module)

    treejs_deps = {name: path for path, name in ctx.attr.treejs_deps.items()}
    args.add("--treejs", treejs_deps["tree.min.js"].files.to_list()[0])
    args.add("--treejs_css", treejs_deps["treejs.min.css"].files.to_list()[0])

    args.add("--out", ctx.outputs.out.path)

    ctx.actions.run(
        inputs = ctx.attr.root[_ClaroModuleInfo].info.files.to_list() + [treejs_dep.files.to_list()[0] for treejs_dep in ctx.attr.treejs_deps.keys()],
        outputs = [ctx.outputs.out],
        arguments = [args],
        progress_message = "Generating ClaroDocs: " + ctx.outputs.out.path,
        executable = ctx.executable.clarodocs_generator_bin,
    )

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
            doc = "The output file to write the generated ClaroDocs HTML to.",
            mandatory = True,
        ),
        "clarodocs_generator_bin": attr.label(
            default = Label("//src/java/com/claro/module_system/clarodocs:clarodocs_generator"),
            allow_files = True,
            executable = True,
            cfg = "host",
        ),
        "treejs_deps": attr.label_keyed_string_dict(
            doc = "Paths to the required treejs JS and CSS to include in generated HTML.",
            allow_files = True,
            default = {
                "//src/java/com/claro/module_system/clarodocs/html_rendering/homepage/treejs:tree.min.js": "tree.min.js",
                "//src/java/com/claro/module_system/clarodocs/html_rendering/homepage/treejs:treejs.min.css": "treejs.min.css",
            }
        ),
    },
)