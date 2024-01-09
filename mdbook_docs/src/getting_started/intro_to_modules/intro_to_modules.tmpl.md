# Intro to Modules

Now, the Hello World program that you wrote in the previous section was extremely simple - just a one-liner in a single
file. Let's add a tiny bit more functionality to your first program as an excuse to learn about Claro's Module System!

Taking inspiration from the starter project's demo program, which printed the following to stdout: 

```
----------------------                                                                                        
| LOOK MA, NO HANDS! |                                                                                        
----------------------  
```

we'll extend our `hello_world.claro` program to also print out the classic greeting in the same boxed styling. We could
of course just copy-paste the demo program's `wrapInBox` function into `hello_world.claro`, but instead, in order to 
avoid having multiple implementations of the same function that could drift over time, we'll walk through the process of
refactoring both programs so that each can share a single function implementation as a common dependency.

## Create `//example:styling.claro`

First thing first, create the file `//example:styling.claro` to hold the `wrapInBox` function definition:

{{EX1}}

## Define a Module API File

Claro Modules are extremely flexible by design (we'll only begin to scratch the surface here) and in order to achieve
that flexibility a Module API file is used to declare which definitions are exported to consumers of the Module. Any 
definition not listed in the Module API file is "private" by default. In this case we just have the one function 
definition so we'll add its signature to the new file `styling.claro_module_api` (the only naming requirement here is 
that it must end with the `.claro_module_api` suffix).

{{EX2}}

As a general rule of thumb, when working in a Claro project, you should prioritize writing documentation for anything
exported in a Module API file. And when reading code, it's advisable to spend most of your time primarily referencing
Module API files rather than their corresponding source files, unless of course you are curious to understand the 
implementation.  

#### Your project should now have the following structure:

{{EX3}}

## Add a `claro_module(name = "styling", ...)` Build Target

The final step in defining a Module in Claro is defining a `claro_module(...)` build target. Add the following to your
`BUILD` file to create a Module by declaring explicitly that the `styling.claro` file implements the interface declared
by `styling.claro_module_api`:

{{EX4}}

### Updated `load(...)` Statement
The `load(...)` statement also needed to be updated to include the newly used `claro_module` Build Rule.

### Added an Explicit Dependency on `//example:styling`
Claro handles dependencies **entirely** within Bazel BUILD files, and `.claro` source files themselves do not have any
mechanism for the traditional `import` style that you will have gotten accustomed to in other languages. This is the key
to Claro's extremely flexible Module system and provides many powerful advantages over the traditional `import` style,
but we won't get any further into that here.

For now, just note that `claro_*()` Build targets all accept an (optional) `deps = {<dep name>: <module target>}` map 
that explicitly declares and names any dependencies the current compilation unit has on any other Module. Note that the
consuming compilation unit is **free to choose any name** to refer to the Module(s) that it depends on. Here we've 
chosen to name the `//example:styling` Module `Style`.

## Update `hello_world.claro` to Use `Style::wrapInBox`

Now we're finally ready to update our Hello World program to wrap its output in a box using its new module dependency!
Update `hello_world.claro` to:

{{EX5}}

## Now Execute Your Updated Hello World!

_Note: The below recording was made with <a href="https://asciinema.org/" target="_blank">asciinema</a> - try pausing
and copying any text._
<script async id="asciicast-630594" src="https://asciinema.org/a/630594.js" data-preload="true" data-start-at="7" data-autoplay="false"></script>

## On Your Own: Update `//example:demo_bin` to Use the New Module

Using what you've learned, it should now be straightforward to update `//example:demo_bin` to also depend on the newly
defined Module so that there's only a single definition of the `wrapInBox` function in your project.

## On Your Own: Refactor `//example:styling` to its Own Directory

This will be a good way to test your understanding of how Claro and Bazel work together. 

_Hint: You can move the Module definition anywhere in the project that you want, but you'll need to update the 
`deps = {...}` declarations to reference its new location._