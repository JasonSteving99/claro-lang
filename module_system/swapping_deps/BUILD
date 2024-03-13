load("//:rules.bzl", "claro_binary", "claro_module")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "swapping_deps",
    doc_template = "swapping_deps.tmpl.md",
    examples = [
        {
            "example": "get_message.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
        {
            "example": "modules_BULD",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": "example.claro",
            "append_output": False,
            "deps": {"Msg": ":look_ma"},
        },
        {
            "example": "binary_hello_BUILD",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": ":hello_out",
            "executable": False,
        },
        {
            "example": "binary_look_ma_BUILD",
            "executable": False,
            "codeblock_css_class": "python",
        },
        {
            "example": ":look_ma_out",
            "executable": False,
        },
        {
            "example": "extra_exports.claro_module_api",
            "executable": False,
            "codeblock_css_class": "claro",
        },
    ],
)

claro_binary(
    name = "test_hello",
    main_file = "example.claro",
    deps = {
        "Msg": ":hello_world",
    }
)
claro_binary(
    name = "test_look_ma",
    main_file = "example.claro",
    deps = {
        "Msg": ":look_ma",
    }
)
claro_module(
    name = "hello_world",
    module_api_file = "get_message.claro_module_api",
    srcs = ["hello_world.claro"],
)
claro_module(
    name = "look_ma",
    module_api_file = "get_message.claro_module_api",
    srcs = ["look_ma.claro"],
)

[
    genrule(
        name = name + "_out",
        outs = [name + "_out.txt"],
        srcs = ["test_" + name + "_deploy.jar"],
        cmd = "$(JAVA) -jar $(SRCS) > $(OUTS)",
        tools = ["@bazel_tools//tools/jdk:current_java_runtime"],
        toolchains = ["@bazel_tools//tools/jdk:current_java_runtime"],
    )
    for name in ["hello", "look_ma"]
]
