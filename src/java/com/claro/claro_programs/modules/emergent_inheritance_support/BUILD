load("//src/java/com/claro:claro_build_rules.bzl", "claro_binary")

claro_binary(
    name = "test_inheritance",
    main_file = "test_inheritance.claro",
    deps = {
        "Dog": "//src/java/com/claro/claro_programs/modules/emergent_inheritance_support/animals/dog:Dog",
        "Cat": "//src/java/com/claro/claro_programs/modules/emergent_inheritance_support/animals/cat:Cat",
    }
)
