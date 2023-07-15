load("//src/java/com/claro:claro_build_rules.bzl", "claro_binary", "claro_module")

# TODO(steving) This is still not working but is a good sanity test validating that the .claro_module dep file can
# TODO(steving) actually be parsed and the exported signatures parsed.
claro_binary(
    name = "binary_with_module_dep_test",
    main_file = "test_main.claro",
    deps = {
        "TestDep": ":module_test",
    },
)

# Test Claro's fledgling First-Class Module system.
claro_module(
    name = "module_test",
    module_api_file = "TestModule.claro_module_api",
    srcs = [
        "TestModuleImpl.claro",
        "TestModuleImpl2.claro",
    ],
)