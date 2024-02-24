load("//stdlib/utils/abstract_modules:abstract_module_internal.bzl", "claro_abstract_module_internal")

CacheWithCustomLoader = \
    claro_abstract_module_internal(
        name = "CacheWithCustomLoader",
        module_api_file = "@claro-lang//stdlib/cache/loader:custom_cache.claro_module_api",
        parameterized_type_names = ["K", "V"],
        default_srcs = [
            "@claro-lang//stdlib/cache/loader:cache_loader.claro_internal",
        ],
        overridable_srcs = {
            "LoadAllImpl": "@claro-lang//stdlib/cache/loader:default_loadAll.claro_internal",
            "ReloadImpl": "@claro-lang//stdlib/cache/loader:default_reload.claro",
        },
        default_deps = {
            "cache": "@claro-lang//stdlib/cache:cache",
        },
        default_exports = ["cache"],
    )
