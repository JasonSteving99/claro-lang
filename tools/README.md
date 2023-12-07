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
$ ./create_claro_project.sh example_claro_project
# Run these commands from some dir in the project tree.
bazel build //example:example_claro_project_bin  # Optional: you could just skip straight to `bazel run ...` below.
bazel run //example:example_claro_project_bin
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
