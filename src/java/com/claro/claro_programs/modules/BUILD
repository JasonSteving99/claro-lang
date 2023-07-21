load("//src/java/com/claro:claro_build_rules.bzl", "claro_binary", "claro_module")

# This is an executable Claro program with transitive dependencies on multiple other claro_module() targets!
claro_binary(
    name = "binary_with_module_dep_test",
    main_file = "test_main.claro",
    deps = {
        "TestDep": ":module_test",
        "TestDep2": ":module_test2",
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

# Here's a module with a transitive dep.
claro_module(
    name = "module_test2",
    module_api_file = "TestModule.claro_module_api",
    srcs = [
        "indirect_addition.claro",
        "TestModuleImpl2.claro",
    ],
    deps = {
        "Addition": ":addition",
    },
)

claro_module(
    name = "addition",
    module_api_file = "addition.claro_module_api",
    srcs = ["addition.claro"],
)