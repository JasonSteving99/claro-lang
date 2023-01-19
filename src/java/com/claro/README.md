# Learn the Syntax by Example
I recommend getting immersed in Claro syntax by just reading (and running) some of the demo programs in `./claro_programs/` [here](https://github.com/JasonSteving99/claro-lang/tree/main/src/java/com/claro/claro_programs).

# Running Claro Programs

You'll have a few options below for running Claro programs, either by building from the latest source directly, or by
using the online REPL.

## Try it Out Online at [riju.codes/claro](https://riju.codes/claro)!

Please keep in mind that in the current state of the world, Riju is generally behind the latest state of Claro
development since I don't control Riju and can't redeploy for each new commit to this repo. If you want the latest of
the latest then read the below to build Claro locally.

## Build from Source with Bazel (Highly Recommended)

#### Compiler Backends

Claro's compiler is designed from the ground up to support multiple backends to allow for various modes of handling the
parsed AST Intermediate Representation. For now Claro supports the following Targets:

### Java Source Target Output

```
$ bazel run claro_compiler_binary -- --java_source --silent
```

(Fully supported - primary development focus)

### REPL

```
$ bazel run claro_compiler_binary -- --repl --silent
```

(Note that this works, but when using the `input()` function in the REPL, you won't be able to see what you're typing as
input, but it will successfully parse it..)

### Interpreted

```
$ bazel run claro_compiler_binary -- --interpreted --silent
```

(Turns out that this actually broken until we can make a change to allow the interpreted mode to read from files instead
of System.in...since it steals input from the program itself meaning that any program using the `input()` stmt doesn't
work...For now it hardcodes using the file `second.claro`.)
