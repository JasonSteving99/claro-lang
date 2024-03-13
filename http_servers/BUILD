load("//:rules.bzl", "claro_module", "claro_binary")
load("//mdbook_docs:docs_with_validated_examples.bzl", "doc_with_validated_examples")

doc_with_validated_examples(
    name = "http_servers",
    doc_template = "http_servers.tmpl.md",
    examples = [
        {
            "example": "ex-http-service.claro",
            "optional_stdlib_deps": ["http"],
            "hidden_setup": "http_dep_workaround.claro",
            "append_output": False,
        },
        {
            "example": "empty_endpoint_handlers_example",
            "optional_stdlib_deps": ["http"],
            "expect_errors": True,
        },
        {
            "example": "endpoint_handlers_example",
            "optional_stdlib_deps": ["http"],
            "deps": {"EndpointHandlerImpls": ":endpoint_handler_impls"},
            "append_output": False,
        },
        {
            "example": "start_the_server_example",
            "executable": False,
            "codeblock_css_class": "claro",
        },
    ],
)


genrule(
    name = "empty_endpoint_handlers_example",
    outs = ["empty_endpoint_handlers_example.claro"],
    srcs = ["ex-http-service.claro", "empty_endpoint_handlers.claro"],
    cmd = "cat $(location ex-http-service.claro) $(location empty_endpoint_handlers.claro) > $(OUTS)",
)
genrule(
    name = "endpoint_handlers_example",
    outs = ["endpoint_handlers_example.claro"],
    srcs = ["ex-http-service.claro", "endpoint_handlers.claro"],
    cmd = "cat $(location ex-http-service.claro) $(location endpoint_handlers.claro) > $(OUTS)",
)
genrule(
    name = "start_the_server_example",
    outs = ["start_the_server_example.claro"],
    srcs = ["ex-http-service.claro", "endpoint_handlers.claro", "start_the_server.claro"],
    cmd = "cat $(location ex-http-service.claro) $(location endpoint_handlers.claro) | sed 's/^/$$$$/' > $(OUTS) && cat $(location start_the_server.claro) >> $(OUTS)",
)

claro_module(
    name = "endpoint_handler_impls",
    module_api_file = "endpoint_handler_impls.claro_module_api",
    srcs = ["endpoint_handler_impls.claro"],
)

genrule(
    name = "start_the_server_impl",
    outs = ["start_the_server_impl.claro"],
    srcs = ["ex-http-service.claro", "endpoint_handlers.claro", "start_the_server.claro"],
    cmd = "cat $(location ex-http-service.claro) $(location endpoint_handlers.claro) $(location start_the_server.claro) > $(OUTS)",
)

# This target was used to generate the Asciinema demo of interacting with this server.
claro_binary(
    name = "greeter_server",
    main_file = "start_the_server_impl",
    optional_stdlib_deps = ["http"],
    deps = {"EndpointHandlerImpls": ":endpoint_handler_impls"},
)