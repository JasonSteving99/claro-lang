load("//src/java/com/claro/stdlib/utils/abstract_modules:abstract_module_internal.bzl", "claro_abstract_module_internal")

CacheWithCustomLoader = \
    claro_abstract_module_internal(
        class_name = "CacheWithCustomLoader",
        module_api_file = "@claro-lang//src/java/com/claro/stdlib/claro/cache/loader:custom_cache.claro_module_api",
        parameterized_type_names = ["K", "V"],
        default_srcs = [
            "@claro-lang//src/java/com/claro/stdlib/claro/cache/loader:cache_loader.claro_internal",
        ],
        overridable_srcs = {
            "LoadAllImpl": "@claro-lang//src/java/com/claro/stdlib/claro/cache/loader:default_loadAll.claro_internal",
            "ReloadImpl": "@claro-lang//src/java/com/claro/stdlib/claro/cache/loader:default_reload.claro",
        },
        default_deps = {
            "cache": "@claro-lang//src/java/com/claro/stdlib/claro/cache:cache",
        },
        default_exports = ["cache"],
    )
