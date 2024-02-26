workspace(name = "claro-lang")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")

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
    patches = ["//patched_jcup:cup_rule_diff.patch", "//patched_jcup:jflex_rule_diff.patch"],
)
load("@jflex_rules//jflex:deps.bzl", "JFLEX_ARTIFACTS")
load("@jflex_rules//third_party:third_party_deps.bzl", "THIRD_PARTY_ARTIFACTS")

http_archive(
    name = "rules_proto",
    sha256 = "dc3fb206a2cb3441b485eb1e423165b231235a1ea9b031b4433cf7bc1fa460dd",
    strip_prefix = "rules_proto-5.3.0-21.7",
    urls = [
        "https://github.com/bazelbuild/rules_proto/archive/refs/tags/5.3.0-21.7.tar.gz",
    ],
)
load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")
rules_proto_dependencies()
rules_proto_toolchains()

# Claro's going to require bootstrapping for at least Dep Module Monomorphization, and in the future will ideally
# iteratively be migrated from an all-Java implementation, to an implementation that uses progressively more Claro. As
# such, a "bootstrapping compiler", defined via a prior release, will be necessary in order to prevent a circular dep
# in Claro's Bazel build which would fail to build.
http_file(
    name = "bootstrapping_claro_compiler_tarfile",
    # In some way, it'd be nicer to make use of https://github.com/JasonSteving99/claro-lang/releases/latest/download/..
    # instead of naming the release explicitly. However, this would make it impossible to cherrypick an old version and
    # rebuild without manual work.
    sha256 = "f1c2911ddd333f96970a23ca4c205afa9f209bc41bd70c4e8386efc6bcda15f3",
    url = "https://github.com/JasonSteving99/claro-lang/releases/download/v0.1.408/claro-cli-install.tar.gz",
)

# ClaroDocs is built atop Google's Closure Templates in order to ensure that I'm not generating unsafe html since the
# intention is for users to be able to trust and host ClaroDocs themselves (particularly relevant since ClaroDocs
# automatically generate inlined docs for all of the binaries dependencies, whether first or 3rd party).
http_archive(
    name = "io_bazel_rules_closure",
    sha256 = "9498e57368efb82b985db1ed426a767cbf1ba0398fd7aed632fc3908654e1b1e",
    strip_prefix = "rules_closure-0.12.0",
    urls = [
        "https://github.com/bazelbuild/rules_closure/archive/0.12.0.tar.gz",
    ],
)

load("@io_bazel_rules_closure//closure:repositories.bzl", "rules_closure_dependencies", "rules_closure_toolchains")
rules_closure_dependencies()
rules_closure_toolchains()

load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")
bazel_skylib_workspace()

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
            "com.github.pcj:google-options:jar:1.0.0",
            "com.google.auto.value:auto-value:1.5.3",
            "com.google.guava:guava:jar:32.1.2-jre",
            "com.google.protobuf:protobuf-java-util:3.24.3",
            "com.googlecode.lanterna:lanterna:3.1.1",
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

            # This addresses unwanted missing StaticLoggerBinder warning logs from SLF4J. This shouldn't be necessary
            # anymore once Claro has proper logging support. See: https://www.slf4j.org/codes.html#StaticLoggerBinder
            "org.slf4j:slf4j-nop:2.0.7",
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

################################################################################
# ANTLR
http_archive(
  name = "rules_antlr",
  sha256 = "26e6a83c665cf6c1093b628b3a749071322f0f70305d12ede30909695ed85591",
  strip_prefix = "rules_antlr-0.5.0",
  urls = ["https://github.com/marcohu/rules_antlr/archive/0.5.0.tar.gz"],
)
load("@rules_antlr//antlr:repositories.bzl", "rules_antlr_dependencies")
rules_antlr_dependencies("4.8")
################################################################################
