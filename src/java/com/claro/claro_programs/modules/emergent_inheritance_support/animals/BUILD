load("//src/java/com/claro:claro_build_rules.bzl", "claro_module")

exports_files(["Animal.claro_module_api", "Animal.claro"])


claro_module(
    name = "Greeter",
    module_api_file = "Greeter.claro_module_api",
    srcs = ["dummy.claro"],
    visibility = [
        "//src/java/com/claro/claro_programs/modules/emergent_inheritance_support:__subpackages__",
    ]
)

genrule(
    name = "dummy",
    outs = ["dummy.claro"],
    cmd = "echo '_ = 1;' > $(OUTS)"
)

genrule(
    name = "greeter_api",
    outs = ["Greeter.claro_module_api"],
    cmd = "echo 'contract Greeter<T> {\n  function getGreeting(t: T) -> string;\n}\n' > $(OUTS)"
)