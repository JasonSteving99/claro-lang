load("//src/java/com/claro:claro_build_rules.bzl", "claro_binary")
load("//src/java/com/claro/module_system/clarodocs:clarodocs_rules.bzl", "clarodocs")

# NOTE: For an even better example of "inheritance" check out the implementation of this same Animals example using the
#       more advanced usage of "Abstract Modules" in //src/java/com/claro/stdlib/utils/abstract_modules/example:test. In
#       a way, Abstract Modules encodes some of the patterns used here to create a generically reusable toolset.
claro_binary(
    name = "test_inheritance",
    main_file = "test_inheritance.claro",
    deps = {
        "Dog": "//examples/claro_programs/modules/emergent_inheritance_support/animals/dog:Dog",
        "Cat": "//examples/claro_programs/modules/emergent_inheritance_support/animals/cat:Cat",
        "Greeter": "//examples/claro_programs/modules/emergent_inheritance_support/animals:Greeter",
    },
)

clarodocs(
    name = "test_inheritance_docs",
    root = ":test_inheritance",
)