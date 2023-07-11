workspace(name = "claro-lang")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

RULES_JVM_EXTERNAL_TAG = "3.3"
RULES_JVM_EXTERNAL_SHA = "d85951a92c0908c80bd8551002d66cb23c3434409c814179c0ff026b53544dab"

http_archive(
    name = "rules_jvm_external",
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    sha256 = RULES_JVM_EXTERNAL_SHA,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

http_archive(
    name = "jflex_rules",
    sha256 = "bd41584dd1d9d99ef72909b3c1af8ba301a89c1d8fdc59becab5d2db1d006455",
    strip_prefix = "bazel_rules-1.8.2",
    url = "https://github.com/jflex-de/bazel_rules/archive/v1.8.2.tar.gz",
    # In order to hack in my own modification to the jflex repo's CUP rules,
    # I'm following the http_archive patching example here:
    # https://bazelbuild.github.io/rules_nodejs/changing-rules.html#patching-the-built-in-release
    patch_args = ["-p1"],
    patches = ["//patched_jcup:cup_rule_diff.patch"],
)
load("@jflex_rules//jflex:deps.bzl", "JFLEX_ARTIFACTS")
load("@jflex_rules//third_party:third_party_deps.bzl", "THIRD_PARTY_ARTIFACTS")

# See this documentation to understand how fetching Maven deps works in Bazel:
# https://github.com/bazelbuild/rules_jvm_external
# When you add a new maven dep run the following command to update new deps:
# $ bazel run @unpinned_maven//:pin
maven_install(
    name = "maven",
    artifacts =
        JFLEX_ARTIFACTS +
        THIRD_PARTY_ARTIFACTS +
        [
            "com.google.auto.value:auto-value:1.5.3",
            "com.google.guava:guava:jar:23.5-jre",
            "com.googlecode.lanterna:lanterna:3.1.1",
            "io.javalin:javalin:4.1.1",
            "org.apache.commons:commons-text:jar:1.1",
            "org.projectlombok:lombok:1.18.20",
            "org.slf4j:slf4j-simple:1.7.31",
            "com.squareup.okhttp3:okhttp:4.11.0",
            # Not using latest retrofit 2.9.0 because it seems there's a JDK warning of illegal reflection in retrofit2.
            # The maintainers responded to this calling it something they explicitly won't fix since it's just a warning
            # but I think that for now it makes Claro look bad, so I'm intentionally downgrading in the meantime until
            # this is resolved. Re: https://github.com/square/retrofit/issues/3341
            "com.squareup.retrofit2:retrofit:2.7.2",
            "com.google.code.gson:gson:2.10.1",

            ############################################################################################################
            # BEGIN ACTIVE J
            #   These deps come in a group, so if the version number ever gets bumped, must do it as a whole.
            ############################################################################################################
            "io.activej:activej-common:5.4.3",
            "io.activej:activej-eventloop:5.4.3",
            "io.activej:activej-http:5.4.3",
            "io.activej:activej-promise:5.4.3",
            ############################################################################################################
            # END ACTIVE J
            ############################################################################################################
        ],
    maven_install_json = "//:maven_install.json",
    repositories = [
        "https://jcenter.bintray.com/",
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
    ],
)

load("@maven//:defs.bzl", "pinned_maven_install")

pinned_maven_install()
