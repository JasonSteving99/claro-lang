load("@aspect_bazel_lib//lib:write_source_files.bzl", "write_source_file")

# Copy the logo to this directory programmatically rather than explicitly so that I don't need another ATTRIBUTION file.
write_source_file(
    name = "logo",
    in_file = "//logo:ClaroLogoFromArrivalHeptapodOfferWeapon1.jpeg",
    out_file = "favicon.png",
    visibility = ["//mdbook_docs/src:__pkg__"],
    suggested_update_target = "//mdbook_docs/src:write_all_docs",
)
