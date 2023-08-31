package(default_visibility = ["//visibility:public"])

alias(
    name = "google-options",
    actual = "@maven//:com_github_pcj_google_options",
)
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

alias(
    name = "slf4j_nop",
    actual = "@maven//:org_slf4j_slf4j_nop"
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


################################################################################
# BEGIN: Setup Bootstrapping Claro Compiler.
################################################################################
java_binary(
    name = "bootstrapping_claro_compiler_binary",
    main_class = "com.claro.ClaroCompilerMain",
    srcs = [":_dummy"], # java_binary() requires some source file, so giving a dummy file.
    deps = [":bootstrapping_claro_compiler_import"],
)
genrule(
    name = "_dummy",
    outs = ["dummy.java"],
    cmd = "echo 'public class dummy {}' > $(OUTS)",
)
java_import(
    name = "bootstrapping_claro_compiler_import",
    jars = [":bootstrapping_claro_compiler.jar"],
)
java_import(
    name = "bootstrapping_claro_builtin_java_deps_import",
    jars = [":bootstrapping_claro_builtin_java_deps_deploy.jar"],
)
genrule(
    name = "bootstrapping_claro_compiler",
    srcs = ["@bootstrapping_claro_compiler_tarfile//file"],
    outs = [
        "bootstrapping_claro_compiler.jar",
        "bootstrapping_claro_builtin_java_deps_deploy.jar",
    ],
    cmd = "tar -xzf $(location @bootstrapping_claro_compiler_tarfile//file) " +
          "&& cat claro_compiler_binary_deploy.jar > $(location bootstrapping_claro_compiler.jar)" +
          "&& cat claro_builtin_java_deps_deploy.jar > $(location bootstrapping_claro_builtin_java_deps_deploy.jar)",
)
################################################################################
# END: Setup Bootstrapping Claro Compiler.
################################################################################
