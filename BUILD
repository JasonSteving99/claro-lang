package(default_visibility = ["//visibility:public"])

alias(
    name = "guava",
    actual = "@maven//:com_google_guava_guava",
)
alias(
    name = "apache_commons_text",
    actual = "@maven//:org_apache_commons_commons_text",
)
alias(
    name = "lanterna",
    actual = "@maven//:com_googlecode_lanterna_lanterna",
)

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


################################################################################
# BEGIN: Setup Lombok.
################################################################################

# Export the lombok plugin as a simple java_library dep.
java_plugin(
    name = "lombok_plugin",
    processor_class = "lombok.launch.AnnotationProcessorHider$AnnotationProcessor",
    generates_api = True,
    deps = [
        "@maven//:org_projectlombok_lombok",
    ],
)

java_library(
    name = "lombok",
    exported_plugins = [
        ":lombok_plugin",
    ],
    neverlink = 1,
    exports = [
        "@maven//:org_projectlombok_lombok"
    ],
)

################################################################################
# END: Setup Lombok.
################################################################################
