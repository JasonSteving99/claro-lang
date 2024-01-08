# Create Your First Claro Project with Bazel!

Claro is fundamentally designed around a deep integration with the extremely powerful 
<a href="https://bazel.build/" target="_blank">Bazel</a> build system - which enables Claro's 
<a href="https://bazel.build/run/build#correct-incremental-rebuilds" target="_blank">incremental builds</a>,
<a href="https://bazel.build/external/overview#bzlmod" target="_blank">external package management</a>, extremely 
flexible module system, and build-time metaprogramming capabilities - so you'll need to do a bit of Bazel-related setup 
to prepare your Claro project. 

**You'll only need to do this once**! Bazel does not mandate monorepo style development, but it really shines when used
in that context. From this single Bazel project, you'll be able to write, build, and run whatever (related or unrelated)
Claro programs as you see fit.

Follow the below steps to set up your first Claro project with Bazel! 

### 1 - Install Bazel (Required)
Simply install Bazel - follow <a href="https://bazel.build/install/bazelisk" target="_blank">these instructions</a> 
to install via Bazelisk.

<div class="warning">
Important: Installing Bazel via Bazelisk makes managing Bazel versions an automated process. It's highly recommended you
don't bother managing Bazel's versioning manually.</div>

_Note: It's an explicit non-goal of Claro to support any other non-Bazel build environment_.

### 2 - Auto-Generate Your Project
Get `create_claro_project.sh` from the 
<a href="https://github.com/JasonSteving99/claro-lang/releases/latest" target="_blank">latest Release</a> 
and run this command:
```
$ ./create_claro_project.sh <project name>
```

_Note: The below recording was made with <a href="https://asciinema.org/" target="_blank">asciinema</a> - try pausing 
and copying any text._
<script async id="asciicast-630055" src="https://asciinema.org/a/630055.js"></script>

<div class="warning">
The first time you build/run a Claro program, you will actually be building the Claro compiler and its dependencies from
source. This may take several minutes the first time, but Bazel will cache the built compiler after that first build.
</div>

You can delete the `create_claro_project.sh` script once you're done with this initial setup.

### 2 (Alternative) - Manually Copy Configuration of Example Project
Follow the example Claro project configuration at
<a href="https://github.com/JasonSteving99/claro-lang/tree/main/examples/bzlmod" target="_blank">examples/bzlmod/</a>.

<div class="warning">
Important: In your MODULE.bazel file, you'll want to choose the latest release published to:
<a href="https://registry.bazel.build/modules/claro-lang" target="_blank">https://registry.bazel.build/modules/claro-lang</a>
</div>

## Supported Operating Systems

As Claro is still firmly in development, **it has only been tested on macOS**. You may run into trouble running it on 
another OS as there are some known portability issues building the Claro compiler from source (currently the only 
supported way to consume the compiler). 

## Your First Claro Program

Continue on to the next section to learn how to build and run your first Claro program!