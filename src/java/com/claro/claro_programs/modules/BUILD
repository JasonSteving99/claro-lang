load("//src/java/com/claro:claro_build_rules.bzl", "claro_binary", "claro_module")

########################################################################################################################
# Here's a trivial demonstration of how Claro's Module System resolves transitive dependencies.
#
# The below Claro program has been decomposed into a (trivial) DAG of module deps as follows:
#
#                             :binary_with_module_dep_test
#                                   /         |         \
#                                  v          |          v
#                         :module_test         \      :module_test2
#                                               \          \
#                                                \          v
#                                                 \---> :addition
#                                                             \
#                                                              v
#                                                         :multi_hop_transitive_dep
########################################################################################################################

# This is an executable Claro program with transitive dependencies on multiple other claro_module() targets!
claro_binary(
    name = "binary_with_module_dep_test",
    main_file = "test_main.claro",
    deps = {
        "Addition": ":addition",
        "TestDep": ":module_test",
        "TestDep2": ":module_test2",
    },
)

# Test Claro's fledgling First-Class Module system.
claro_module(
    name = "module_test",
    module_api_file = "TestModule.claro_module_api",
    srcs = [
        "TestModuleImpl.claro",
        "TestModuleImpl2.claro",
    ],
    debug = True,
)

# Here's a module with a chain of transitive deps that will all need to pulled up into any consumers for the compiler
# to understand transitive dep module types exported by this one.
claro_module(
    name = "module_test2",
    module_api_file = "TestIndirection.claro_module_api",
    srcs = [
        "indirect_addition.claro",
    ],
    deps = {
        "Addition": ":addition",
    },
    exports = ["Addition"],
)

claro_module(
    name = "addition",
    module_api_file = "addition.claro_module_api",
    srcs = ["addition.claro"],
    deps = {
        "MultiHopDep": ":multi_hop_transitive_dep",
    },
    exports = ["MultiHopDep"],
)

# Another just-for-the-sake-of-it example of Claro inheriting a powerful codegen facility from Bazel's standard tooling.
claro_module(
    name = "multi_hop_transitive_dep",
    module_api_file = "multi_hop_transitive_dep.claro_module_api",
    # TODO(steving) I need to upgrade the claro_module() target to allow api-only modules when only type defs are exported.
    srcs = [":noop_src"]
)

genrule(
    name = "multi_hop_module_api",
    outs = ["multi_hop_transitive_dep.claro_module_api"],
    cmd = "echo 'newtype MultiHopInt : int' > $(OUTS)",
)

genrule(
    name = "noop_src",
    outs = ["noop_src.claro"],
    cmd = "echo '_ = 1;' > $(OUTS)",
)

