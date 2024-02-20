# Build Time Metaprogramming

Claro takes the stance that relatively sophisticated Build-time logic can be a great enabler of significant flexibility.
In particular, even very simple Build-time code generation can be used to achieve extremely convenient code reuse
without forcing the core Claro programming language itself to become more and more complex over time to directly
represent more and more complex design patterns.

This section seeks to demonstrate how Bazel's configuration language (that you'll already be using to declare
`claro_module(...)` and `claro_binary(...)` targets in `BUILD` files) can be used to write logic that will
programmatically determine at Build-time the program structure that will be converted to an executable at Compile-time. 

## Build vs Compile Phases

The first distinction to make here is a subtle difference between "Build" and "Compile" time in the Claro ecosystem.
All statically compiled languages have a "Compile" phase where the final program's source code is evaluated by the
language's compiler and eventually converted into an actual executable, and this is no different in Claro. The unique
aspect of Claro is that it's been intentionally designed with a tight integration with its Build system 
<a href="https://bazel.build/" target="_blank">Bazel</a> in mind, and so your program's source files are first processed
by Bazel, potentially executing arbitrary Build-time logic of your choosing _before any source code is ever passed to
the Claro compiler itself_.

As we'll go into more detail on in this section, Bazel enables you to write all sorts of arbitrary file-manipulations or
script executions during this Build phase. This enables you to do any manner of source code transformations or even code
generation from statically validated sources of truth. And, thanks to Bazel's correctness guarantees, you're able to
string together arbitrary graphs of Build logic that will be reliably reproduced on each Build (with each step 
benefiting from Bazel's caching support for incremental Builds). Only after all Build steps have completed successfully
does the Build phase end and the Compile phase begin with the final dependency graph of `claro_binary(...)` and
`claro_module(...)` targets evaluated by the Claro compiler.

**Continue on to the next sections where we'll go into more details to demonstrate some ways you can leverage Build-time
metaprogramming to gain more dynamic control over the Claro programs you create.**