# Tools

This directory will hold simple cli tools that can be used to simplify some of your interactions with Claro. Read the
tool-specific descriptions below for more details on what's currently available.

## codegen.sh

This script generates files for a basic Claro program with instructions on how to run to get someone coding quickly.

### Usage:

```bash
$ ./codegen.sh <path> <name>
```

Example
```bash
$ ./codegen.sh test hello_world
$ bazel build //test/hello_world:hello_world # Optional: you could just skip straight to `bazel run ...` below.
$ bazel run //test/hello_world:hello_world
$ tree test
test
└── hello_world
    ├── BUILD
    ├── README.md
    ├── hello_world.claro
    └── input.txt
```