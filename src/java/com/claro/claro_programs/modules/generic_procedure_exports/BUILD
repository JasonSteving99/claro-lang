load("//src/java/com/claro:claro_build_rules.bzl", "claro_binary", "claro_module")


claro_module(
    name = "generic_procedure_export",
    module_api_file = "generic_procedure_export.claro_module_api",
    srcs = ["generic_procedure_export.claro"],
    debug = True,
)

claro_binary(
    name = "test_generic_exports",
    main_file = "test_generic_exports.claro",
    deps = {
        "Dep": ":generic_procedure_export"
    },
    debug = True,
)