# Tools

This directory will hold simple cli tools that can be used to simplify some of your interactions with Claro. Read the
tool-specific descriptions below for more details on what's currently available.

## codegen.sh

This script generates files for a basic Claro project with instructions on how to run to get you coding quickly.

### Usage:

```bash
$ ./create_claro_project.sh <project name>
```

Example
```bash
$ ./create_claro_project.sh test hello_world
$ bazel build //test/hello_world:hello_world # Optional: you could just skip straight to `bazel run ...` below.
$ bazel run //test/hello_world:hello_world
----------------------
| LOOK MA, NO HANDS! |
----------------------
$ tree -a example_claro_project                                                                                                                                                                                                 +
example_claro_project
├── .bazelrc
├── MODULE.bazel
├── README.md
└── example
    ├── BUILD
    ├── example_claro_project.claro
    └── input.txt
```
