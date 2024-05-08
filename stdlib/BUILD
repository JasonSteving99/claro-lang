load("//src/java/com/claro:claro_build_rules_internal.bzl", "CLARO_STDLIB_MODULES")
load("//stdlib/utils/expand_template:expand_template.bzl", "expand_template")
load("@aspect_bazel_lib//lib:write_source_files.bzl", "write_source_file")

# Generate a simple Markdown doc for each StdLib module by simply templating its .claro_module_api file into the page.
# This is suuuper basic and completely non-interactive, but at least does give users a way to look at the modules with
# syntax highlighting.
GENERATED_STDLIB_MODULE_DOCS_TARGETS = [(
    genrule(
        name = "{0}_module_name".format(module_name),
        outs = ["{0}_module_name.txt".format(module_name)],
        cmd = "echo {0} > $(OUTS)".format(module_name),
    ),

    expand_template(
        name = "{0}_module_docs".format(module_name),
        template = "stdlib_module.tmpl.md",
        substitutions = {
            "module_name": "{0}_module_name".format(module_name),
            "module_api_file": package_relative_label("//{0}:{1}.claro_module_api".format(package_relative_label(module).package, module_name)),
        },
        out = "{0}_module.templatted.md".format(module_name),
    ),

    write_source_file(
        name = "write_{0}_module_docs".format(module_name),
        in_file = "{0}_module_docs".format(module_name),
        out_file = "{0}_module.generated_docs.md".format(module_name),
        visibility = ["//mdbook_docs/src:__pkg__"],
        suggested_update_target = "//mdbook_docs/src:write_all_docs",
    )
 )

for module_name, module in CLARO_STDLIB_MODULES.items()]

write_source_file(
    name = "write_all_stdlib_docs",
    additional_update_targets = ["write_{0}_module_docs".format(module_name) for module_name in CLARO_STDLIB_MODULES],
    visibility = ["//mdbook_docs/src:__pkg__"],
)

# Generate the StdLib's Table of Contents for the MdBook SUMMARY.md file.
MDBOOK_TOC = "\n".join([
    "    - [{0}](./stdlib/{0}_module.generated_docs.md)".format(module_name)
    for module_name in CLARO_STDLIB_MODULES.keys()
])
genrule(
    name = "mdbook_stdlib_toc",
    outs = ["mdbook_stdlib_toc.txt"],
    cmd = "echo -e '{0}' > $(OUTS)".format(MDBOOK_TOC),
    visibility = ["//mdbook_docs/src:__pkg__"],
)