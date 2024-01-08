# Understanding the Starter Project

The `create_claro_project.sh` script generated several files: 

{{EX1}}

Let's take a brief tour through each file to get a high level understanding of what's going on.

_If you're already familiar with Bazel, jump ahead to writing your 
[first Claro program](../first_program/first_program.md)._


<div class="warning">
<i>You do not need to be a Bazel expert to get up to speed with Claro! But, if you want a deeper understanding of Bazel as
a whole, check out Bazel's official 
<a href="https://bazel.build/concepts/build-ref" target="_blank">concepts guide</a>.</i>
</div>

## MODULE.bazel
_See: <a href="https://bazel.build/rules/lib/globals/module" target="_blank">Official Bazel reference</a>_ - This file 
marks the root of your Bazel project.

{{EX2}}

### `module(name = "example-claro-module")`

This is the one place where you'll see the term "module" overloaded to refer to Bazel's concept of 
<a href="https://bazel.build/external/module" target="_blank">Modules</a> relating to Bazel's external package 
management solution. So, the name you pick for your top-level `module(name = "...")` declaration should be something 
that you would be ok with using to publicly present your project to downstream users if you chose to publish your 
project to the <a href="https://registry.bazel.build/" target="_blank">Bazel Central Registry</a> later on.

### `bazel_dep(name = "claro-lang", version = "0.1.409")`

This file is where you will declare your external dependencies for Bazel to resolve at build time. Every Claro project 
will need to declare an external dependency on the `claro-lang` project to get access to the Build Rules (e.g. 
`claro_binary()` and `claro_module()`) as well as the compiler itself. Keeping your installation of Claro up-to-date is
as simple as bumping the version number listed here.

Claro has been published to the BCR at
<a href="https://registry.bazel.build/modules/claro-lang" target="_blank">https://registry.bazel.build/modules/claro-lang</a>.
Check for new releases there to make sure that you're using the latest and greatest.

## .bazelversion
_See: <a href="https://github.com/bazelbuild/bazelisk?tab=readme-ov-file#how-does-bazelisk-know-which-bazel-version-to-run" target="_blank">
Official Bazelisk reference</a>_ - This file configures Bazelisk to use the declared Bazel version.

{{EX3}}

Claro depends on Bzlmod which was introduced in Bazel version 6, so you'll need to use at least version 6.

## .bazelrc

_See: <a href="https://bazel.build/run/bazelrc" target="_blank">Official Bazel reference</a>_ - This file is used to 
configure optional Bazel flags.

{{EX4}}

### `common --enable_bzlmod`

This configures Bazel to opt in to enabling the
<a href="https://bazel.build/external/overview#bzlmod" target="_blank">Bzlmod</a>, external package manager. This will 
be necessary in all Claro projects to at least enable Bazel to resolve your dependency on the Claro compiler. 

### `common --java_runtime_version=remotejdk_11`

This configures Bazel to download a remote version of the JVM to execute compiled Claro programs. Technically, you can
<a href="https://bazel.build/docs/bazel-and-java#compile-using-jdk" target="_blank">opt in to using a local Java install</a>,
but keeping this flag as is ensures that you're running a JVM version that Claro's actually been tested against.

## BUILD

_See: <a href="https://bazel.build/build/style-guide" target="_blank">Official Bazel reference</a>_ - BUILD files are
the fundamental building block of a Bazel project. Here you'll define "build targets" representing components of your
program and their dependencies.

{{EX5}}

### `load("@claro-lang//:rules.bzl", "claro_binary")`

This loads (a.k.a. "imports") the `claro_binary()` Build Rule from the `rules.bzl` 
<a href="https://bazel.build/extending/concepts" target="_blank">Bazel extension file</a> located in the root directory
of the `claro-lang` project. After this `load`, you're able to define `claro_binary()` targets in this BUILD file by
calling it just as you would a function in any other programming language (albeit with mandatory named parameters).

### `claro_binary(...)`

As mentioned above, this declares a build target that represents an executable Claro program (`*_binary()` is the
conventional naming of executable build targets in the Bazel ecosystem). 

#### `name = "demo_bin"`

All Bazel build targets include a mandatory `name = "..."` parameter - in combination with the full path from the 
project root, this specific build target can be uniquely referenced as `//example:demo_bin`. Using this name, you can
execute Bazel build/run commands from the command line. 

You can **build** the target to have Bazel invoke the Claro compiler to verify that your program is valid and if so 
generate the executable program artifacts that can be invoked separately:
```
bazel build //example:demo_bin
```

During local development you can directly **build and run** the target by using the below command which will trigger
Bazel to build the target and then upon success invoke the built executable program automatically: 
```
bazel run //example:demo_bin
```

#### `main_file = "demo.claro"`

Claro programs begin execution by running top-level statements of a given "main file" top-down, rather than looking for
some special `main` function.

#### `resources = { "Input": "input.txt", }`

This declares that this program should bundle the file `input.txt` into the final compiled Jar file so that it's 
available at runtime no matter where the program is run. It makes this resource file available as `resources::Input` in
the compiled program. Find more details about resources in the Reference Guide.

## input.txt

Just a resource file read by the demo program.

{{EX6}}

## demo.claro

The main Claro file that contains the code to be executed. 

{{EX7}}

This program just reads in the contents of the [`input.txt` resource file](#resources---input-inputtxt-), trims extra
whitespace, converts it to all caps, wraps it in a box of "-" characters, and prints it to stdout. 

Note the calls to functions like `files::readOrPanic` and `strings::trim` are calling into functions declared in dep
modules. In this case there's no explicit mention of those dependencies in the `claro_binary(...)` target declaration
because `files` and `strings` are modules in the 
<a href="https://github.com/JasonSteving99/claro-lang/tree/main/stdlib" target="_blank">stdlib</a> so no explicit 
dependency is necessary.