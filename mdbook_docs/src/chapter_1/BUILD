# BUILD
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")
exports_files(["hello_world.claro", "hello_world.tmpl.md", "BUILD"])

doc_with_validated_examples(
    name = "hello_world",
    doc_template = "hello_world.tmpl.md",
    examples = ["hello_world.claro"],
)
