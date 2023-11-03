# Learn the Syntax by Example

I recommend getting immersed in Claro syntax by just reading (and running) some of the demo programs
in [examples/claro_programs/](https://github.com/JasonSteving99/claro-lang/tree/main/examples/claro_programs).

Comprehensive docs are also in progress at https://jasonsteving99.github.io/claro-lang/.

# Create your own Claro Project with Bazel!

Simply install Bazel - instructions to install via Bazelisk can be found [here](https://bazel.build/install/bazelisk).

Then follow the example Claro project configuration
at [examples/bzlmod/](https://github.com/JasonSteving99/claro-lang/tree/main/examples/bzlmod).

# Building Claro Programs

As Claro projects are fundamentally built on the [Bazel build system](https://bazel.build/about/intro), building a Claro
program simply requires running a single `bazel build ...` command. For example, if you wanted to build the example
program at `//examples/claro_programs/atoms.claro`, you can simply run the corresponding
`claro_binary(name = "atoms", ...)` target located at `//examples/claro_programs/BUILD` using the below command from any
directory within the claro-lang project:

```commandline
$ bazel build //examples/claro_programs:atoms
```

This will invoke the claro compiler to do type checking and codegen for the specified Claro program. You can build any
Bazel target this way.

# Running Claro Programs

A Claro program can be executed in two different ways:

### Executing via `$ bazel run ...`

This is the most straightforward approach, and the one that you'll use most often when in local development. Simply use
the `bazel run ...` command to have Bazel build the specified executable Claro program, and then immediately run the
generated executable. Note that this command is only supported on Bazel targets that produce an executable output, in
Claro's case, that means you can only `bazel run ...` a `claro_binary()` target, and not a `claro_module()` target.

It's worth noting that this approach prioritizes build time, but does not generate a portable executable. For a portable
executable, see the section below.

### Building and Running a Prebuilt Executable Jar

In order to build a portable, executable Jar file that can be run anywhere where a JVM is available, you will have to
`bazel build ...` a "deploy Jar". This can be done by building the `foo_deploy.jar` Bazel target that the
`claro_binary(name = foo, ...)` rule generates automatically. For example, to build a portable executable for the
claro_binary() target at `//examples/claro_programs:atoms`:

```commandline
$ bazel build //examples/claro_programs:atoms_deploy.jar
```

The above command will produce output like the following:

```
➜  claro-lang git:(main) ✗ bazel build //examples/claro_programs:atoms_deploy.jar                                                                                                                                                                     +
INFO: Analyzed target //examples/claro_programs:atoms_deploy.jar (0 packages loaded, 0 targets configured).
INFO: Found 1 target...
Target //examples/claro_programs:atoms_deploy.jar up-to-date:
  bazel-bin/examples/claro_programs/atoms_deploy.jar
INFO: Elapsed time: 0.174s, Critical Path: 0.00s
INFO: 1 process: 1 internal.
INFO: Build completed successfully, 1 total action
```

Now, the generated "deploy Jar" is listed as being located at `bazel-bin/examples/claro_programs/atoms_deploy.jar`. You
can now execute it using the following command:

```commandline
java -jar bazel-bin/examples/claro_programs/atoms_deploy.jar
```

That's it! Now, this "deploy Jar" can be taken and run anywhere with a JVM.
