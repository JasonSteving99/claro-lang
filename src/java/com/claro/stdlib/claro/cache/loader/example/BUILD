load("//src/java/com/claro/stdlib/claro/cache/loader:custom_cache_loader.bzl", "cache_with_custom_loader")
load("//src/java/com/claro:claro_build_rules.bzl", "claro_binary")

# This demonstrates the metaprogramming power of the combination of Claro and Bazel. This target is programmatically
# declaring a totally custom module that builds instances of `cache::Cache<K,V>` that use the customized loaders defined
# here. Object Oriented "Classes" are literally completely unnecessary to get this type of code reuse.
cache_with_custom_loader(
    name = "custom_reload_cache",
    keyType = "string",
    valueType = "string",
    loadImpl = ":load.claro",
    reloadImpl = ":reload.claro",
)

# This target generates a new module that has completely different behavior than the one above, this time accepting the
# default reloadImpl.
cache_with_custom_loader(
    name = "default_reload_cache",
    keyType = "string",
    valueType = "string",
    loadImpl = ":load.claro",
)

claro_binary(
    name = "test_bin",
    main_file = "test.claro",
    deps = {
        "cache": "//src/java/com/claro/stdlib/claro/cache:cache",
        "CustomReloadCache": ":custom_reload_cache",
        "DefaultReloadCache": ":default_reload_cache",
        "Duration": "//src/java/com/claro/stdlib/claro/time/duration:duration",
    },
)