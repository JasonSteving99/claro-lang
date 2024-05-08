load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "first_program",
    doc_template = "first_program.tmpl.md",
    examples = [
        {
            "example": "//mdbook_docs/src/chapter_1:hello_world.claro",
            "append_output": False,
        },
        {
            "example": ":tree",
            "executable": False,
        },
        {
            "example": ":example_BUILD",
            "executable": False,
            "codeblock_css_class": "claro",
        },
    ],
)

# Ensure that the files that get templated into the documentation represent the *actual* files the user will see, by
# running the actual create_claro_project.sh script.
genrule(
    name = "list_generated_files",
    outs = [
        "tree",
        "example_BUILD",
    ],
    srcs = [
        ":hello_world_binary_target.txt",
        "//mdbook_docs/src/chapter_1:hello_world.claro",
        "//tools:create_claro_project_script",
    ],
    cmd = """bash $(location //tools:create_claro_project_script) demo > /dev/null \
             && cp $(location //mdbook_docs/src/chapter_1:hello_world.claro) demo/example \
             && cat $(location :hello_world_binary_target.txt) >> demo/example/BUILD \
             && tree -a demo > $(location tree) \
             && cp demo/example/BUILD $(location example_BUILD)
    """
)