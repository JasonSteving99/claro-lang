load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    "claro_binary",
)

# TODO(steving) At build time, this is a pretty inefficient way to do this as I'm dynamically generating a new
# TODO(steving)   templating binary for each call. Once Claro has better support for reading dynamic files that aren't
# TODO(steving)   listed in `resources = {...}`, this could be turned into a single binary and the macro could become
# TODO(steving)   a proper first class rule instead.
def expand_template(name, template, substitutions = {}, out = "", visibility = []):
    if len(out) == 0:
        fail("Missing required attribute: 'out'")
    staticContents = \
        """
        var tmpl = files::readOrPanic(resources::Tmpl);
        {0}
        print(tmpl);
        """
    dynamicContents = "\n".join([
        """
        tmpl = strings::replace(tmpl, "\\{{\\{{{0}}}}}", files::readOrPanic(resources::{0}));
        """.format(s)
        for s in substitutions.keys()
    ])
    native.genrule(
        name = name + "_expand_template",
        outs = [name + "_expand_template.claro"],
        cmd = "echo -e '{0}' >> $(OUTS)".format(staticContents.format(dynamicContents))
    )
    claro_binary(
        name = name + "_bin",
        main_file = name + "_expand_template.claro",
        resources = dict(substitutions, **{"Tmpl": template}),
    )
    native.genrule(
        name = name,
        srcs = [name + "_bin_deploy.jar"],
        outs = [out],
        toolchains = ["@bazel_tools//tools/jdk:current_host_java_runtime"],
        cmd = "$(JAVA) -jar $(SRCS) > $(OUTS)",
        visibility = visibility,
    )