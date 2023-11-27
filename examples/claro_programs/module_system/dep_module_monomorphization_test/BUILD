load("//:rules.bzl", "claro_module", "claro_binary")

# TODO(steving) TESTING!!! Currently due to the way that Bazel has implemented the builtin java_library() and
# TODO(steving)   java_binary() rules, Claro's use of a common "extra" top-level `$DepModuleMonomorphizations`
# TODO(steving)   non-public class in every module that calls generic procedures from its deps is broken as
# TODO(steving)   the *_deploy.jar finally produced by the top-level java_binary() will actually dedupe the
# TODO(steving)   `$DepModuleMonomorphizations` class from each of the java_library()'s below it and only accept
# TODO(steving)   the last one generated.
# TODO(steving)   While this is currently causing a bug in the functionality of monomorphization, this actually
# TODO(steving)   represents a big code-size optimization opportunity. If each .claro_module can encode the complete set
# TODO(steving)   of monomorphizations that it generated in its $DepModuleMonomorphization class (which is still
# TODO(steving)   necessary for it's corresponding java_library() target to successfully build), then all of that
# TODO(steving)   codegen can be blindly copied into any consuming Claro targets that depend on it. This will have a
# TODO(steving)   two-fold optimization improvement. First, it will potentially allow downstream consumer targets to
# TODO(steving)   actually completely skip dep module monomorphizations that it happened to already recieve from its
# TODO(steving)   deps which should have the potential to improve compile times! Second, it will result in all of the
# TODO(steving)   duplicated $DepModuleMonomorphization classes generated below the root claro_binary() node to get
# TODO(steving)   completely overwritten by the single class at the root of the dep graph that will have accumulated ALL
# TODO(steving)   of the monomorphizations necessary for the entire program!
claro_binary(
    name = "top",
    main_file = "top.claro",
    deps = {
        "Bottom": ":bottom",
        "MidA": ":MidA",
        "MidB": ":MidB",
    }
)

claro_module(
    name = "MidA",
    module_api_file = "MidA.claro_module_api",
    srcs = ["MidA.claro"],
    deps = {
        "Bottom": ":bottom",
    },
)

claro_module(
    name = "MidB",
    module_api_file = "MidB.claro_module_api",
    srcs = ["MidB.claro"],
    deps = {
        "Bottom": ":bottom",
    },
)

claro_module(
    name = "bottom",
    module_api_file = "bottom.claro_module_api",
    srcs = ["bottom.claro"],
)