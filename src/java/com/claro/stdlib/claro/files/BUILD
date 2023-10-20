load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    "claro_module_internal",
    "claro_binary",
    "CLARO_STDLIB_MODULES")

# TODO(steving) WIP! I STILL NEED TO INTEGRATE THIS INTO THE STDLIB ONCE THE BOOTSTRAPPING COMPILER CAN HANDLE THIS NEW FUNCTIONALITY!
claro_binary(
    name = "test_files",
    main_file = "test_files.claro",
    deps = {
        "files": ":files",
    },
    resources = {
        "TestFile": "test_resource.txt",
    },
)

claro_module_internal(
    name = "files",
    module_api_file = "files.claro_module_api",
    srcs = ["files.claro_internal"],
    deps = {
        "std": CLARO_STDLIB_MODULES["std"],
    },
    exports = ["std"],
    visibility = ["//visibility:public"],
)