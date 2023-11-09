load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    "bootstrapped_claro_module_internal",
    "claro_binary",
    "CLARO_STDLIB_MODULES",
)

bootstrapped_claro_module_internal(
    name = "cache",
    module_api_file = ":overall_cache.claro_module_api",
    srcs = [
        "cache.claro_internal",
        "//src/java/com/claro/stdlib/claro/cache/builder:cache_builder.claro_internal",
    ],
    deps = {
        "duration": "@claro-lang//src/java/com/claro/stdlib/claro/time/duration:duration",
        "std": CLARO_STDLIB_MODULES["std"],
    },
    exports = ["duration", "std"],
    exported_custom_java_deps = [
        "//:caffeine",
        "//:future_converter",
    ],
    visibility = ["//visibility:public"],
    debug = True
)

# TODO(steving) `module_api_file` should be concatenative like `srcs`.
genrule(
    name = "overall_cache_api",
    outs = ["overall_cache.claro_module_api"],
    srcs = [
        "cache.claro_module_api",
        "//src/java/com/claro/stdlib/claro/cache/builder:cache_builder.claro_module_api",
    ],
    cmd = "cat $(SRCS) > $(OUTS)"
)

claro_binary(
    name = "test_cache_builder",
    main_file = "test_cache_builder.claro",
    deps = {
        "cache": ":cache",
        "duration": "@claro-lang//src/java/com/claro/stdlib/claro/time/duration:duration",
    },
    debug = True,
)
