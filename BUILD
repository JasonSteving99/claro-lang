package(default_visibility = ["//visibility:public"])

alias(
    name = "guava",
    actual = "@maven//:com_google_guava_guava",
)
alias(
    name = "protobuf",
    actual = "@com_google_protobuf_protobuf_java//:com_google_protobuf_protobuf_java",
)
alias(
    name = "apache_commons_text",
    actual = "@maven//:org_apache_commons_commons_text",
)
alias(
    name = "lanterna",
    actual = "@maven//:com_googlecode_lanterna_lanterna",
)
alias(
    name = "javalin",
    actual = "@maven//:io_javalin_javalin",
)
alias(
    name = "slf4j",
    actual = "@maven//:org_slf4j_slf4j_simple",
)
alias(
    name = "okhttp",
    actual = "@maven//:com_squareup_okhttp3_okhttp"
)
alias(
    name = "retrofit",
    actual = "@maven//:com_squareup_retrofit2_retrofit"
)
alias(
    name = "gson",
    actual = "@maven//:com_google_code_gson_gson"
)
alias(
    name = "activej_common",
    actual = "@maven//:io_activej_activej_common"
)
alias(
    name = "activej_eventloop",
    actual = "@maven//:io_activej_activej_eventloop"
)
alias(
    name = "activej_http",
    actual = "@maven//:io_activej_activej_http"
)
alias(
    name = "activej_promise",
    actual = "@maven//:io_activej_activej_promise"
)

exports_files(["CLARO_VERSION.txt"])

################################################################################
# BEGIN: Setup AutoValue.
################################################################################

# Export the autovalue plugin as a simple java_library dep.
java_plugin(
    name = "autovalue_plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        "@maven//:com_google_auto_value_auto_value",
    ],
)

java_library(
    name = "autovalue",
    exported_plugins = [
        ":autovalue_plugin",
    ],
    neverlink = 1,
    exports = [
        "@maven//:com_google_auto_value_auto_value",
    ],
)

################################################################################
# END: Setup AutoValue.
################################################################################

