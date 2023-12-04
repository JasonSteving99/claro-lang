load("@aspect_bazel_lib//lib:write_source_files.bzl", "write_source_file")

write_source_file(
    name = "write_all_docs",
    additional_update_targets = [
        "//mdbook_docs/src/static_typing/builtin_colls/list_type:list_type",
        "//mdbook_docs/src/static_typing/builtin_colls/map_type:map_type",
        "//mdbook_docs/src/static_typing/builtin_colls/set_type:set_type",
        "//mdbook_docs/src/static_typing/builtin_colls/tuple_type:tuple_type",
    ],
)
