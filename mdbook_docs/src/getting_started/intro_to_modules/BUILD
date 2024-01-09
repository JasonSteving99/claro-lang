load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "intro_to_modules",
    doc_template = "intro_to_modules.tmpl.md",
    examples = [
        {
            "example": ":styling.claro",
            "executable": False,
        },
        {
            "example": ":styling.claro_module_api",
            "executable": False,
        },
        {
            "example": "tree",
            "executable": False,
        },
        {
            "example": "updated_BUILD",
            "executable": False,
        },
        {
            "example": "updated_hello_world.claro",
            "executable": False,
        },
    ],
)

# Ensure that the files that get templated into the documentation represent the *actual* files the user will see, by
# running the actual create_claro_project.sh script.
genrule(
    name = "list_generated_files",
    outs = [
        "tree",
        "updated_BUILD",
    ],
    srcs = [
        ":styling.claro",
        ":styling.claro_module_api",
        ":styling_module_target.txt",
        ":updated_BUILD_load_stmt.txt",
        "//mdbook_docs/src/chapter_1:hello_world.claro",
        "//tools:create_claro_project_script",
    ],
    cmd = """bash $(location //tools:create_claro_project_script) demo > /dev/null \
             && cp $(location //mdbook_docs/src/chapter_1:hello_world.claro) demo/example \
             && cp $(location //mdbook_docs/src/getting_started/intro_to_modules:styling.claro) demo/example \
             && cp $(location //mdbook_docs/src/getting_started/intro_to_modules:styling.claro_module_api) demo/example \
             && tree -a demo > $(location tree) \
             && cat $(location updated_BUILD_load_stmt.txt) > $(location updated_BUILD) \
             && tail -n +2 demo/example/BUILD >> $(location updated_BUILD) \
             && cat $(location styling_module_target.txt) >> $(location updated_BUILD)
    """
)
