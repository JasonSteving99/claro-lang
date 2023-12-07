load("//stdlib/utils/expand_template:expand_template.bzl", "expand_template")

# Auto-generate the script from the examples/bzlmod/ files so that I can ensure that this is always up-to-date.
expand_template(
    name = "create_claro_project_script",
    template = "create_claro_project.tmpl.sh",
    substitutions = {
        "DOT_BAZEL_RC": "//examples/bzlmod:.bazelrc",
        "MODULE_DOT_BAZEL": "//examples/bzlmod:MODULE.bazel",
    },
    out = "create_claro_project.sh",
)
