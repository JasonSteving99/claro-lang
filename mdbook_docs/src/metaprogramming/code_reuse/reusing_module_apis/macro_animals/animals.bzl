# animals.bzl
load("@claro-lang//:rules.bzl", "claro_module")

def Animal(name, srcs):
    native.genrule( # In .bzl files you'll need to prefix builtin rules with `native.`
        name = "{0}_api".format(name),
        srcs = ["animal.claro_module_api", "{0}_cons.claro_module_api".format(name)],
        outs = ["{0}.claro_module_api".format(name)],
        cmd = "cat $(SRCS) > $(OUTS)"
    )
    claro_module(
        name = name,
        module_api_file = ":{0}_api".format(name),
        srcs = srcs,
        deps = {"AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds"},
        # This Module is referenced in this Module's API so must be exported.
        exports = ["AnimalSounds"],
    )
