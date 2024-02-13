# Flags

An incredibly common pattern in many software projects is parsing command line flags on startup to configure the
behavior of a program. For example in backend web services the same service frequently gets reconfigured via flags to
run in various different environments (e.g. test/dev/prod). However, in spite of the pattern's universality, most
languages seem to ignore the fact and leave Flag parsing as an exercise for the user. This realistically leaves users
either running off to download some 3rd-party library or writing some often poorly maintained boilerplate parsing code
themselves. Claro aims to provide a lightweight Flag parsing mechanism as a first-class language feature so that you can
skip most of the manual toil for such a simple need.

Claro's Flags are a special case of [Static Values](../static_values/static_values.generated_docs.md) that can be
defined and exported by a Module API[^1]: 

{{EX1}}

Then, just like any other Static Value, it can be referenced directly by anyone with a dependency on the defining Module
as in the example below:

{{EX2}}

{{EX3}}

Flags are different than general Static Values simply in the way their values are instantiated. Rather than implementing
a provider that will be automatically run to instantiate the value, Flags are actually automatically parsed from the
command line args passed to the program at runtime. **In the example above, the Flag wasn't explicitly set when the
program was run, so the value was defaulted to the empty string**.

## Setting a Flag Value on the Command Line

As there are multiple ways to run Claro programs during development, you'll need to know how to actually set Flag values
using each approach.

### Passing Flags to Programs Executed via `bazel run ...`

Of course, as you've seen in the 
[Getting Started Guide](../../../getting_started/first_program/first_program.generated_docs.md#now-execute-your-program)
the easiest way to run a Claro program during development is using the `bazel run ...` command. But because Bazel 
_itself_ accepts command line Flags, you'll need to explicitly indicate _which_ command line args should be consumed by
Bazel and which should be passed along to the Claro program. You'll do this by simply using a standalone `--`. Bazel
consumes every arg to the left, and anything following gets passed along to the program you're trying to run.

_Note: The below recording was made with <a href="https://asciinema.org/" target="_blank">asciinema</a> - try pausing
and copying any text._
<script async id="asciicast-639326" src="https://asciinema.org/a/639326.js" data-preload="true" data-autoplay="false"></script>

### Passing Flags to Deploy Jar

Instead, you can build your program as an executable "Deploy Jar" and execute the Jar using the `java` command, passing
command line Flags as you would to any other command:

_Note: The below recording was made with <a href="https://asciinema.org/" target="_blank">asciinema</a> - try pausing
and copying any text._
<script async id="asciicast-639444" src="https://asciinema.org/a/639444.js" data-preload="true" data-autoplay="false"></script>

## Deriving Static Values From Flags

Now, the power of Flags is often exposed when used to determine the initialization of Static Values. For example,
expanding upon the simple `env` example above, we could export another Static Value, and determine its value based on
whatever value was assigned to the `env` Flag on the command line.

{{EX4}}

{{EX5}}

And now, a test program could reference the Static Value, and the program's output will be dependent on the Flag value
passed on the command line at runtime:

{{EX6}}

_Note: The below recording was made with <a href="https://asciinema.org/" target="_blank">asciinema</a> - try pausing
and copying any text._
<script async id="asciicast-639488" src="https://asciinema.org/a/639488.js" data-preload="true" data-autoplay="false"></script>

## Supported Flag Types

Claro has to manually emit logic to parse command line args, and as such there's currently only support for parsing the
following basic types that are most likely to be found in command line args: 
- `boolean`
- `string`
- `int`
- `[string]`

Claro will statically reject any Flags of unsupported types. For example, Claro won't automatically parse arbitrary
structs from the command line. (Although it's likely that in the future Claro will standardize its string encoding of
all types and provide some extended support for automatically decoding them from strings).

---

[^1]: Command line Flag parsing in most other languages can only be done by explicitly handling the command line args
list in the program's "main method" (or equivalent). But in Claro, Flags can be arbitrarily defined by *any Module* in
the entire program. The only thing to keep in mind is that the very nature of Flags being given on the command line 
means that their names *must* be globally unique. So, if you plan to include Flags in a Module that you're publishing
for a wide audience, make sure that you use somehow try to ensure that your Flag names can at least be _reasonably_
expected to be globally unique. One suggestion would be to prefix all Flag names with the published name of your Bazel
module that's been pushed to the 
[Bazel Central Registry](../../../getting_started/understanding_starter_project/understanding_starter_project.generated_docs.md#modulename--example-claro-module).
