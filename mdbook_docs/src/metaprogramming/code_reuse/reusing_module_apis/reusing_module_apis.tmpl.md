# Reusing Module APIs

<div class="warning">

This is a long section, but it's foundational to a deep understanding of the full expressive power you have available to
you at Build time. You're encouraged to read through this in full! But remember, while you may sometimes end up 
consuming Modules that were _defined_ using these advanced features, **you'll never be _forced_ to directly use any
Build time metaprogramming feature yourself**. These will always be conveniences for more advanced users.
</div>

## Multiple Implementations of a Module API

The most basic, and also most important form of reuse in Claro codebases will be in the form of multiple Modules sharing
a common API. This doesn't require any special syntax or setup whatsoever, once you've defined a valid
`.claro_module_api` file any number of Modules may implement that API. Each `claro_module(...)` definition simply needs
to declare its `module_api_file = ...` to reference the same exact `.claro_module_api` file.

For example, the following API:

{{EX1}}

Can be implemented multiple times, by more than one Module:

{{EX2}}

**In general, the Build targets declared above will be totally sufficient!**

## Going Deeper
To make this example more compelling, the API definition above declares that any Module implementing the API will export
a type that includes a name field, but may configure its own internal state as it wishes. If you read the API closely,
however, you may notice that as presently defined there'd be no way for any dependent Module to actually interact with
this API as defined, because there's no way to instantiate the `opaque newtype InternalState`[^1]. So, in order to
actually make this API useful, implementing Modules would need to somehow explicitly export some Procedure that gives
dependents the ability to instantiate the `InternalState`. You'll notice that care has been taken to make sure that
Claro's API syntax is flexible enough to allow for multiple APIs to be conceptually (or in this case, literally)
concatenated to create one larger API for a Module to implement. So that's exactly what we'll do here, with each module
exporting an additional procedure from its API to act as a "constructor" for its `opaque` type.

{{EX3}}

<div class="warning">

In the future `claro_module(...)` will accept a list of `.claro_module_api` files instead of a single file to make this
pattern easier to access without having to manually drop down to a `genrule(...)` to concatenate API files.
</div>

And now, importantly, multiple Modules implementing the same API can coexist in the same Claro program with no conflict!

{{EX4}}

{{EX5}}

<div class="warning">

_Read more about [Dynamic Dispatch](../../../generics/contracts/dynamic_dispatch/dynamic_dispatch.generated_docs.md) if
you're confused how the above Contract Procedure call works._
</div>

## Expressing the Above Build Targets More Concisely 

Now, you'd be right to think that the above Build target declarations are extremely verbose. And potentially worse, they
also contain much undesirable duplication that would have to kept in sync manually over time. Thankfully, Bazel provides
**_many_** ways to address both of these issues. 

Remember that Bazel's `BUILD` files are written using Starlark, a subset of Python, so we have a significant amount of
flexibility available to us when declaring Build targets! We'll walk through a few different options for defining these
targets much more concisely.

### Using List Comprehension to Define Multiple Similar Targets at Once

The very first thing we'll notice is that the vast majority of these targets are duplicated. So, as programmers, our
first thought should be to ask how we can factor out the common logic, to avoid repeating ourselves. The below rewritten
`BUILD` file does a much better job of making the similarities between the `Cat` and `Dog` modules explicit, and also
prevents them from drifting apart accidentally over time.

{{EX6}}

### Declaring a Macro in a `.bzl` File to Make This Factored Out Build Logic Portable

Now let's say that you wanted to declare another "Animal" in a totally separate package in your project. You could
easily copy-paste the Build targets found in the previous `BUILD` file... but of course, this would invalidate our goals
of avoiding duplication. So instead, as programmers our spider-senses should be tingling that we should factor this
common logic not just into the loop (list comprehension), but into a full-blown function that can be reused and called
from anywhere in our project. Bazel thankfully gives us access to defining so-called
<a href="https://bazel.build/rules/macro-tutorial" target="_blank">"Macros"</a> that fill exactly this purpose.

The Build targets in the prior examples could be factored out into a Macro definition in a `.bzl` (Bazel extension file)
like so:

{{EX7}}

And then, the macro can be used from `BUILD` files like so[^2]:

{{EX8}}

It couldn't possibly get much more concise than this! If you find yourself in a situation where you'll be defining lots
of very similar Modules, it's highly recommended that you at least consider whether an approach similar to this one will
work for you.

## Swapping Dependencies at Build Time Based on Build Flags

TODO(steving) I think that I probably want to move this to be its own top-level section.

TODO(steving) Fill out this section describing how this is effectively Dependency Injection handled at Build time rather
than depending on heavyweight DI frameworks.

---
[^1]: For more context, read about [Opaque Type's](../../../module_system/module_apis/type_definitions/opaque_types/opaque_types.generated_docs.md).

[^2]: In practice, if you want a Bazel Macro to be reusable outside the Build package in which its `.bzl` file is 
defined, you'll need to use fully qualified target label. E.g. `//full/path/to:target` rather than `:target`, as the
latter is a "relative" label whose meaning is dependent on the Build package it's used in.   
