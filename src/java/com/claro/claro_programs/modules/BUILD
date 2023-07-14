load("//src/java/com/claro:claro_build_rules.bzl", "claro_library")

# Test Claro's fledgling First-Class Module system.
claro_library(
    name = "module_test",
    module_api_file = "TestModule.claro_module_api",
    src = [
        "TestModuleImpl.claro",
        "TestModuleImpl2.claro",
    ],
    debug = True,
)
