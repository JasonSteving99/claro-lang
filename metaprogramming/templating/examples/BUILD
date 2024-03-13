# BUILD
load("@claro-lang//stdlib/utils/expand_template:expand_template.bzl", "expand_template")
exports_files(["ex1.claro.tmpl", "BUILD"])

expand_template(
    name = "ex1",
    template = "ex1.claro.tmpl",
    out = "ex1.claro",
    substitutions = {
        # Replace {{HOMEPAGE_HTML}} with the contents of the html file generated below.
        "HOMEPAGE_HTML": "homepage_single_line.html",
    },
    visibility = ["//mdbook_docs/src/metaprogramming/templating:__pkg__"],
)

# Learn more about genrule at: https://bazel.build/reference/be/general#genrule
genrule(
    name = "homepage_single_line",
    srcs = ["homepage.html"],
    outs = ["homepage_single_line.html"],
    # Bash script to remove all newlines and escape double-quotes.
    cmd = "cat $(SRCS) | tr '\\n' ' ' | sed 's/\"/\\\\\"/g' > $(OUTS)",
)