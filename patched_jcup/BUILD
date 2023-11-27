package(default_visibility = ["//visibility:public"])

exports_files(["cup_rule_diff.patch", "jflex_rule_diff.patch"])

################################################################################
# BEGIN: Patch CUP. I need it to split do_action method on l.t. the hardcoded
#        limit of 300 actions b/c otherwise CUP is generating a do_action_part
#        method that triggers Java's per-method code size limit.
################################################################################

genrule(
    name = "patched_cup_jar",
    srcs = ["@maven//:com_github_vbmacher_java_cup", "java_cup/emit.java"],
    cmd = " cp $(location @maven//:com_github_vbmacher_java_cup) $@ " +
          ' && $(JAVABASE)/bin/javac -classpath ".:$(location @maven//:com_github_vbmacher_java_cup)" $(location java_cup/emit.java) -d . ' +
          " && $(JAVABASE)/bin/jar -uf $@ java_cup/emit.class ",
    tools = ["@bazel_tools//tools/jdk:current_java_runtime"],
    toolchains = ["@bazel_tools//tools/jdk:current_java_runtime"],
    outs = ["patched_cup.jar"]
)

java_binary(
    name = "patched_cup_bin",
    main_class = "java_cup.Main",
    runtime_deps = [":patched_cup_jar"],
)

################################################################################
# END: Patch CUP.
################################################################################
