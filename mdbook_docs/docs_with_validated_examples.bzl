load("//:rules.bzl", "claro_binary")
load("//src/java/com/claro:claro_build_rules_internal.bzl", "claro_expected_errors")
load("//stdlib/utils/expand_template:expand_template.bzl", "expand_template")
load("@aspect_bazel_lib//lib:write_source_files.bzl", "write_source_file")

def doc_with_validated_examples(name, doc_template, examples = []):
    substitutions = {}
    for i, ex in enumerate(examples):
        example_target = "{0}_EX{1}".format(name, i + 1)
        if type(ex) == "string":
            validated_claro_example(
                name = example_target,
                example_num = i + 1,
                main_file = ex,
            )
        elif type(ex) == "dict":
            validated_claro_example(
                name = example_target,
                example_num = i + 1,
                main_file = ex["example"],
                hidden_setup = ex.setdefault("hidden_setup", None),
                hidden_cleanup = ex.setdefault("hidden_cleanup", None),
                append_output = ex.setdefault("append_output", True),
                expect_errors = ex.setdefault("expect_errors", False),
                executable = ex.setdefault("executable", True),
                codeblock_css_class = ex.setdefault("codeblock_css_class", ""),
                deps = ex.setdefault("deps", {}),
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

def validated_claro_example(
        name, example_num, main_file, hidden_setup = None, hidden_cleanup = None, append_output = True, expect_errors = False, executable = True, codeblock_css_class = "", deps = {}):
    if type(hidden_setup) == "string":
        hidden_setup = [hidden_setup]
    main_with_optional_hidden_cleanup = main_file
    if hidden_cleanup:
        main_with_cleanup = name + "_main_with_cleanup.claro"
        native.genrule(
            name = name + "_main_with_cleanup",
            outs = [main_with_cleanup],
            srcs = [main_file, hidden_cleanup],
            cmd = """
                cat $(location {main_file}) | tr -d "$$" > $(OUTS) \
                && echo "" >> $(OUTS) \
                && cat $(location {cleanup}) >> $(OUTS)
                """.format(
                main_file = main_file, cleanup = hidden_cleanup)
        )
        main_with_optional_hidden_cleanup = main_with_cleanup
    else: # Simply deleting any occurrences of "$$" in the main program so that hidden lines can be supported.
        main = name + "_main.claro"
        native.genrule(
            name = name + "_main",
            outs = [main],
            srcs = [main_file],
            cmd = 'cat $(location {main_file}) | tr -d "$$" > $(OUTS)'.format(
                main_file = main_file, cleanup = hidden_cleanup)
        )
        main_with_optional_hidden_cleanup = main
    if expect_errors:
        build_rule = claro_expected_errors
    else:
        build_rule = claro_binary
    if executable:
        build_rule(
            name = "{0}_example".format(name),
            main_file = main_with_optional_hidden_cleanup,
            srcs = hidden_setup if hidden_setup else [],
            deps = deps,
        )
    else:
        append_output = False
    native.genrule(
        name = "{0}".format(name),
        outs = ["{0}.validated_claro_example".format(name)],
        srcs = [
            main_file,
        ] + ([("{0}_example.errs" if expect_errors else "{0}_example_deploy.jar").format(name)] if executable else []),
        cmd = """
            echo "#### _Fig {example_num}:_" > $(location {name}.validated_claro_example) \
            && echo "---" >> $(location {name}.validated_claro_example) \
            && echo '```{codeblock_css_class}' >> $(location {name}.validated_claro_example) \
            && printf "%s" "$$(< $(location {main_file}))" >> $(location {name}.validated_claro_example) {maybe_append_output}
        """.format(
            name = name,
            example_num = example_num,
            codeblock_css_class = "claro" if executable else codeblock_css_class,
            main_file = main_file,
            maybe_append_output = \
                """\
                && echo '' >> $(location {name}.validated_claro_example) \
                && echo '```' >> $(location {name}.validated_claro_example) \
                && echo '_{output_msg}:_' >> $(location {name}.validated_claro_example) \
                && echo '```{output_codeblock_css_class}' >> $(location {name}.validated_claro_example) \
                && printf "%s" {output_cmd} | cat >> $(location {name}.validated_claro_example) \
                && echo '' >> $(location {name}.validated_claro_example) \
                && echo '```' >> $(location {name}.validated_claro_example) \
                && echo '---' >> $(location {name}.validated_claro_example)
                """.format(
                    name = name,
                    output_msg = "Compilation Errors" if expect_errors else "Output",
                    output_codeblock_css_class = "compilation-errs" if expect_errors else "",
                    output_cmd =
                        ('"$$(cat $(location {name}_example.errs))"' if expect_errors
                        else '"$$($(JAVA) -jar $(location {name}_example_deploy.jar))"')
                            .format(name = name)
                    ) if append_output else
                """\
                && echo '' >> $(location {name}.validated_claro_example) \
                && echo '```' >> $(location {name}.validated_claro_example) \
                && echo '---' >> $(location {name}.validated_claro_example)
                """.format(name = name)
        ),
        tools = ["@bazel_tools//tools/jdk:current_java_runtime"],
        toolchains = ["@bazel_tools//tools/jdk:current_java_runtime"],
        visibility = ["//mdbook_docs:__subpackages__"],
    )
