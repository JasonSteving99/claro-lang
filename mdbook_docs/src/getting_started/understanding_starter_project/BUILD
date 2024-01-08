load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "understanding_starter_project",
    doc_template = "understanding_starter_project.tmpl.md",
    examples = [
        { "example": "tree", "executable": False, },
        { "example": "example_MODULE_dot_bazel", "executable": False, },
        { "example": "example_dot_bazelversion", "executable": False, },
        { "example": "example_dot_bazelrc", "executable": False, },
        { "example": "example_BUILD", "executable": False, },
        { "example": "example_input_dot_txt", "executable": False, },
        { "example": "example_demo_dot_claro", "executable": False, },
    ],
)

# Ensure that the files that get templated into the documentation represent the *actual* files the user will see, by
# running the actual create_claro_project.sh script.
genrule(
    name = "list_generated_files",
    outs = [
        "tree",
        "example_MODULE_dot_bazel",
        "example_dot_bazelversion",
        "example_dot_bazelrc",
        "example_BUILD",
        "example_input_dot_txt",
        "example_demo_dot_claro",
    ],
    srcs = ["//tools:create_claro_project_script"],
    cmd = """bash $(location //tools:create_claro_project_script) demo > /dev/null \
             && tree -a demo > $(location tree) \
             && cp demo/MODULE.bazel $(location example_MODULE_dot_bazel) \
             && cp demo/.bazelversion $(location example_dot_bazelversion) \
             && cp demo/.bazelrc $(location example_dot_bazelrc) \
             && cp demo/example/BUILD $(location example_BUILD) \
             && cp demo/example/input.txt $(location example_input_dot_txt) \
             && cp demo/example/demo.claro $(location example_demo_dot_claro)
    """
)