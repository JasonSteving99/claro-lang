load("//:rules.bzl", "claro_binary")
load("//stdlib/utils/expand_template:expand_template.bzl", "expand_template")
load("@aspect_bazel_lib//lib:write_source_files.bzl", "write_source_file")

def doc_with_validated_examples(name, doc_template, examples = []):
    substitutions = {}
    for i, ex in enumerate(examples):
        example_target = "{0}_EX{1}".format(name, i + 1)
        if type(ex) == "string":
            validated_claro_example(
                name = example_target,
                main_file = ex,
            )
        elif type(ex) == "dict":
            validated_claro_example(
                name = example_target,
                main_file = ex["example"],
                hidden_setup = ex.setdefault("hidden_setup", None),
                append_output = ex.setdefault("append_output", True)
            )
        substitutions["EX{0}".format(i + 1)] = example_target
    generated = "{0}_generated.md".format(name)
    expand_template(
        name = "expand_{0}".format(name),
        template = doc_template,
        substitutions = substitutions,
        out = generated
    )
    write_source_file(
        name = name,
        in_file = generated,
        out_file = "{0}.generated_docs.md".format(name),
        visibility = ["//mdbook_docs/src:__pkg__"],
        suggested_update_target = "//mdbook_docs/src:write_all_docs",
    )

def validated_claro_example(name, main_file, hidden_setup = None, append_output = True):
    if type(hidden_setup) == "string":
        hidden_setup = [hidden_setup]
    claro_binary(
        name = "{0}_example".format(name),
        main_file = main_file,
        srcs = hidden_setup if hidden_setup else [],
    )
    native.genrule(
        name = "{0}".format(name),
        outs = ["{0}.validated_claro_example".format(name)],
        srcs = [main_file, "{0}_example_deploy.jar".format(name)],
        cmd = """
            cat $(location {main_file}) > $(location {name}.validated_claro_example) {maybe_append_output}
        """.format(
            name = name,
            main_file = main_file,
            maybe_append_output = \
                """\
                && echo "\n### OUTPUT:" >> $(location {name}.validated_claro_example) \
                && $(JAVA) -jar $(location {name}_example_deploy.jar) | sed 's/^/# /' | cat >> $(location {name}.validated_claro_example)
                """.format(name = name) if append_output else ""
        ),
        tools = ["@bazel_tools//tools/jdk:current_java_runtime"],
        toolchains = ["@bazel_tools//tools/jdk:current_java_runtime"],
        visibility = ["//mdbook_docs:__subpackages__"],
    )
