# Abstract Modules

In the past couple sections we've worked through examples of some fairly complex Build time metaprogramming to generate
Modules that share some common behaviors between them. Having those low-level Build tools in your back pocket is
something that may very well come in handy during your Claro development journey. However, in general, it's worth
acknowledging that there's some inherent complexity in the prior approaches. It's my hope that the community will
standardize around some well-defined set of Build design patterns that are encoded into well-known, standardized
interfaces (Bazel Macros/Rules) to abstract away the low-level complexity underneath.

In this section, I'll try to demonstrate what such a standardized encoding might look like for the design pattern
demonstrated in the prior sections.

## Limitations of the Approach in the Prior Sections

The `Animal(...)` Macro defined in the previous sections was extremely rigid. It encoded exactly one specific code 
structure. It was arguably a very useful structure, but if we wanted to create an `Animal(...)` that deviated even
slightly from the expected structure, you'd either have to go and refactor the Macro definition itself and all usages to
add support for new behaviors, or you'd just have to fall back to manually defining a Module, losing all Build level
code sharing that you were trying to achieve with the standardized `Animal(...)` Macro.

All that said, the biggest limitation of the approach in the prior sections is that **_it was bespoke_**. While all the
customizability that Build time metaprogramming gives you blows the design space wide open, it also makes it that much
harder for anyone unfamiliar with the domain to follow what's going on.

## Abstracting Away the "Abstract Module" Pattern Itself

Arguably, the `Animal(...)` macro from the previous sections could be described as an encoding of an "Abstract Module"
(in a sense vaguely similar to Java's "Abstract Classes" - minus the object-orientation). "Abstract" in the sense that
some portions of all "Animal" Modules are known before even knowing the "concrete Animal" Modules that you'll
specifically build later on. But there's nothing about this concept itself that's unique to "Animals". All sorts of
categories of similar Modules can be imagined, and they could potentially *all* benefit from a similar "Abstract" base
encoding that later gets specialized for each concrete Module.

Largely as a _draft_ demonstration of what a standardized encoding of this "Abstract Module" design pattern _could_ look
like, Claro provides a `claro_abstract_module(...)` Bazel Macro. Now, the 
[`Animal(...)` Macro](../reusing_module_apis/reusing_module_apis.generated_docs.md#declaring-a-macro-in-a-bzl-file-to-make-this-factored-out-build-logic-portable) 
can be auto-generated in a few lines by simply calling the `claro_abstract_module(...)` Macro.

{{EX1}}

## Override Flexibility

On top of being a standardized encoding of this design pattern, "Abstract Modules" provide an additional mechanism for
various components of the Module to be override-able. In the `Animal = claro_abstract_module(...)` declaration above,
the `overridable_srcs = {...}` arg lists a few different _named_ components that have default implementations provided
as `.claro` source files that can be optionally overridden by any concrete `Animal(...)` usages. For the sake of
demonstration, the "Abstract Animal Module" has been decomposed into a relatively fine granularity, allowing significant
customization to downstream users of the Macro.

So now the `Animal(...)` macro can be used very similarly as in the previous sections, but with some slightly different
arguments:

{{EX2}}

The first notable detail is that the idea of extending Module APIs is now encoded directly into the "Abstract Module"
Macros returned by `claro_abstract_module(...)` in the form of the `api_extensions = [...]` parameter. So now, we didn't
need to manually concatenate api files using a Bazel `genrule(...)` as we 
[did in the prior sections](../reusing_module_apis/reusing_module_apis.generated_docs.md#fig-9). Then, notice that
the concrete `cat` and `dog` Animal Modules now implicitly inherit the default `AnimalSoundsImpl` implementation, while
explicitly overriding `InternalStateAndConstructor` and `MakeNoiseImpl` with custom implementations. Now, these Module
definitions can be used exactly the same as they were when defined using the approach(es) from the prior sections.

As one final motivating example, to demonstrate something that this _new_ `Animal(...)` implementation can do that the
prior implementation(s) couldn't, we can also define a new Animal Module that overrides the default `AnimalSounds`
Contract implementation, by overriding `AnimalSoundsImpl`:

{{EX3}}

{{EX4}}

And now, our demo program can start use the `platypus` Module just as it was using the `dog` and `cat` Modules 
previously:

{{EX5}}

{{EX6}}

## Additional Knobs & Implementation Details

The point of this section is really to _demonstrate_ some possibilities available to all Claro users interested in
writing Bazel Macros to encode relatively complex design patterns. And, I think we can agree that being able to 
hand-roll the very concept of _inheritance_ without having to make a single change to the Claro compiler itself is a
rather powerful capability!

But to say it once more, this is all meant as a demonstration, rather than encouragement of specific usage of this
`claro_abstract_module(...)` Macro. So, we won't dive any further into the implementation details of how this prototype
works, and we won't even go into the full range of capabilities that this prototype currently supports. However, if
you're sufficiently interested that you really wanted to know more, feel free to check out 
<a href="https://github.com/JasonSteving99/claro-lang/blob/main/stdlib/utils/abstract_modules/abstract_module_internal.bzl" target="_blank">the implementation</a>
yourself! You'll probably learn a lot about Bazel in the process of reading through it, so it could be enlightening.