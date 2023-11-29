load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_import_external")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_file")

visibility("private")

def _non_module_deps_impl(ctx):
  http_archive(
    name = "jflex_rules",
    url = "https://github.com/jflex-de/bazel_rules/archive/v1.8.2.tar.gz",
    sha256 = "bd41584dd1d9d99ef72909b3c1af8ba301a89c1d8fdc59becab5d2db1d006455",
    strip_prefix = "bazel_rules-1.8.2",
    patches = [
      "//patched_jcup:cup_rule_diff.patch",
      "//patched_jcup:jflex_rule_diff.patch",
    ],
    patch_args = [
      "-p1"
    ],
  )
  http_file(
    name = "bootstrapping_claro_compiler_tarfile",
    sha256 = "13c0b407bb1aa989df3c4766b9d5f30138d98f9e45d7bedd9265b34e6fae7796",
    url = "https://github.com/JasonSteving99/claro-lang/releases/download/v0.1.355/claro-cli-install.tar.gz",
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
# -- repo definitions -- #

non_module_deps = module_extension(implementation = _non_module_deps_impl)
