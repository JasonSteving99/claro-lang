load("//src/java/com/claro:claro_build_rules.bzl", "claro_module")

# Test Claro's fledgling First-Class Module system.
claro_module(
    name = "module_test",
    module_api_file = "TestModule.claro_module_api",
    srcs = [
        "TestModuleImpl.claro",
        "TestModuleImpl2.claro",
    ],
)