load("@aspect_bazel_lib//lib:write_source_files.bzl", "write_source_file")

def render_graphs(name, srcs):
    write_targets = []
    update_all_name = name

    for src in srcs:
        if not src.endswith(".claro"):
            fail("All srcs must end in .claro. Found: " + src)
        name = src[:-len(".claro")]
        native.genrule(
            name = name + "_graphs_md",
            srcs = ["//antlr/graphs:test_parser_deploy.jar", src],
            outs = [name + "-graphs_gen.md"],
            tools = ["@bazel_tools//tools/jdk:current_java_runtime"],
            toolchains = ["@bazel_tools//tools/jdk:current_java_runtime"],
            cmd = "$(JAVA) -jar $(location //antlr/graphs:test_parser_deploy.jar) -f $(location {src}) -o $(OUTS)".format(src = src),
        )
        write_source_file(
            name = "write_{0}_graphs".format(name),
            in_file = name + "-graphs_gen.md",
            out_file = name + "-graphs.md",
        )
        write_targets.append("write_{0}_graphs".format(name))

    write_source_file(
        name = update_all_name,
        additional_update_targets = write_targets,
    )
