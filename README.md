<div align="left">
  <img src="https://github.com/JasonSteving99/claro-lang/blob/main/logo/ClaroLogoFromArrivalHeptapodOfferWeapon1.jpeg" width=200 height=200>
</div>

# Claro

Claro is a statically compiled JVM language whose goal is first and foremost to provide you with the tools for reasoning
about program design and construction using a very clear and direct mental model - data flowing through a systems of
steps where new data may be produced, and existing data may be updated by the system at each step. This stated mental
model is intentionally simple. Where it may be vague, I hope to add clarity in the rest of these intro docs.

Where other languages tend to mostly fall into the "Object-Oriented" (Java, Python, etc) paradigm, or the "Functional
Programming" (Haskell, Elm, Roc, etc) paradigm, Claro eschews both of these extremes and lands somewhere more closely
resembling a mix of procedural and declarative programming. Claro aims to be easily digestible to programmers coming
from other paradigms by actually striving to add as few totally novel features as possible - only adding net-new ideas
where they can be easily understandable to any user with moderate effort and only after the net-new idea has proven to
provide some substantial, and observable benefit in the real world. Claro is not an esoteric language to be marvelled at
by experts, or language geeks (like myself). Claro is a practical language whose mission is to help make writing
readable, extensible, and performant programs significantly easier than one would achieve using existing languages and
tools. Rather than depend heavily on layers of frameworks to achieve things like dependency injection, safe concurrency
and more, Claro gives you powerful capabilities out of the box.

Learning Claro will involve a bit of unlearning of previous language principles, but will leave you with a single,
well-lit path.

# Read the [Comprehensive User Docs](https://jasonsteving99.github.io/claro-lang/)

Please understand that these docs are a work in progress, and while they do cover a large chunk of the language
features, there is still more documentation to come including better examples and clearer explanations.

# Learn Claro By Example!

Check out the [example Claro programs](https://github.com/JasonSteving99/claro-lang/tree/main/examples/claro_programs).

You may be interested in checking out the 
[solutions](https://github.com/JasonSteving99/claro-lang/tree/main/examples/claro_programs/advent_of_code_2023) to this
year's [Advent of Code](adventofcode.com) in Claro!

# Create your own Claro Project with Bazel!
### 1 - Install Bazel (Required)
Simply install Bazel - instructions to install via Bazelisk can be found [here](https://bazel.build/install/bazelisk).

### 2 - Auto-Generate Your Project
Get `create_claro_project.sh` from the [latest Release](https://github.com/JasonSteving99/claro-lang/releases/latest) 
and run this command:
```
$ ./create_claro_project.sh <project name>
```
More details at [tools/README.md](tools/README.md)

### 2 (Alternative) - Manually Copy Configuration of Example Project
Follow the example Claro project configuration at 
[examples/bzlmod/](https://github.com/JasonSteving99/claro-lang/tree/main/examples/bzlmod).

_NOTE_: In your MODULE.bazel file, you'll want to choose the latest release published to: 
https://registry.bazel.build/modules/claro-lang

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

# Try it Out in a GitHub Codespace!

[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/JasonSteving99/claro-lang?quickstart=1)
