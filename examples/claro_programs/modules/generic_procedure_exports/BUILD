load("//src/java/com/claro:claro_build_rules.bzl", "claro_binary", "claro_module")
load("//src/java/com/claro/module_system/clarodocs:clarodocs_rules.bzl", "clarodocs")


clarodocs(
    name = "generic_procedure_exports_docs",
    root = ":test_generic_exports",
)

claro_binary(
    name = "test_generic_exports",
    main_file = "test_generic_exports.claro",
    deps = {
        "Dep": ":generic_procedure_export",
        "OtherDep": "other_module",
    },
)
claro_module(
    name = "other_module",
    module_api_file = "other_module.claro_module_api",
    srcs = ["other_module.claro"],
    deps = {
        "Foo": ":generic_procedure_export",
    },
    exports = ["Foo"]
)
claro_module(
    name = "generic_procedure_export",
    module_api_file = "generic_procedure_export.claro_module_api",
    srcs = ["generic_procedure_export.claro"],
    deps = {
        "TransitiveDep": ":transitive_generic_procedure_export",
    },
    debug = True,
)
claro_module(
    name = "transitive_generic_procedure_export",
    module_api_file = "transitive_generic_procedure_export.claro_module_api",
    srcs = ["transitive_generic_procedure_export.claro"],
    debug = True,
)