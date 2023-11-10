load(
    "//src/java/com/claro:claro_build_rules_internal.bzl",
    "claro_module_internal",
    "CLARO_STDLIB_MODULES",
)

def cache_with_custom_loader(
        name,
        keyType,
        valueType,
        loadImpl,
        loadAllImpl = "@claro-lang//src/java/com/claro/stdlib/claro/cache/loader:default_loadAll.claro_internal",
        reloadImpl = "@claro-lang//src/java/com/claro/stdlib/claro/cache/loader:default_reload.claro",
        deps = {},
        visibility = [],
        debug = False):
    # Generate an api file that has the type aliases for Key and Value types.
    native.genrule(
        name = name + "_api",
        srcs = ["@claro-lang//src/java/com/claro/stdlib/claro/cache/loader:custom_cache.claro_module_api"],
        outs = [name + ".claro_module_api"],
        cmd = """
            _typedefs='
            alias K : {0}
            alias V : {1}
            ' && echo $$_typedefs $$(cat $(SRCS)) > $(OUTS)
        """.format(keyType, valueType)
    )
    claro_module_internal(
        name = name,
        module_api_file = name + ".claro_module_api",
        srcs = [
            "@claro-lang//src/java/com/claro/stdlib/claro/cache/loader:cache_loader.claro_internal",
            loadImpl,
            loadAllImpl,
            reloadImpl,
        ],
        deps = dict(deps, **{
            "cache": "@claro-lang//src/java/com/claro/stdlib/claro/cache:cache",
        }),
        exports = ["cache"],
        exported_custom_java_deps = [
            "@claro-lang//:caffeine",
            "@claro-lang//:future_converter",
        ],
        visibility = visibility,
        debug = debug,
    )