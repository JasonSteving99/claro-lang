load("//src/java/com/claro/stdlib/utils/abstract_modules/example/animal:animal.bzl", "Animal")
load("//:rules.bzl", "claro_binary", "claro_docs")

Animal(
    name = "dog",
    type_params = {"State": "struct { isHappy: boolean }"},
    override = {"MakeNoiseImpl": "dog_make_noise_impl.claro"},
)

Animal(
    name = "cat",
    type_params = {"State": "struct { favoriteInsult: string }"},
    override = {"MakeNoiseImpl": "cat_make_noise_impl.claro"},
)

claro_binary(
    name = "test",
    main_file = "test.claro",
    deps = {
        "Dog": ":dog",
        "Cat": ":cat",
        "AnimalSounds": "//src/java/com/claro/stdlib/utils/abstract_modules/example/animal:animal_sounds",
    }
)

claro_docs(
    name = "test_docs",
    root = ":test",
)