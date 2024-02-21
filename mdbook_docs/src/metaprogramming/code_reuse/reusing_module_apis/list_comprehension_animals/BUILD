# BUILD
load("@claro-lang//:rules.bzl", "claro_module", "claro_binary")
exports_files(["BUILD"], visibility = ["//mdbook_docs/src/metaprogramming/code_reuse/reusing_module_apis:__pkg__"])

[ # This list-comprehension should feel very reminiscent of Claro's own comprehensions.
    [ # Generate multiple targets at once by declaring them in a list or some other collection.
        genrule(
            name = "{0}_api".format(name),
            srcs = ["animal.claro_module_api", "{0}_cons.claro_module_api".format(name)],
            outs = ["{0}.claro_module_api".format(name)],
            cmd = "cat $(SRCS) > $(OUTS)"
        ),
        claro_module(
            name = name,
            module_api_file = ":{0}_api".format(name),
            srcs = srcs,
            deps = {"AnimalSounds": "//stdlib/utils/abstract_modules/example/animal:animal_sounds"},
            # This Module is referenced in this Module's API so must be exported.
            exports = ["AnimalSounds"],
        )
    ]
    for name, srcs in {"Dog": ["dog.claro"], "Cat": ["cat.claro"]}.items()
]