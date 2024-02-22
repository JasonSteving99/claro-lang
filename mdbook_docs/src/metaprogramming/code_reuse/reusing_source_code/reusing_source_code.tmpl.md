# Reusing Source Code

Continuing to consider the "Animals" example from the 
[previous section](../reusing_module_apis/reusing_module_apis.generated_docs.md), let's consider a simple refactoring.

As a reminder, previously, calls to `AnimalSounds::makeNoise(...)` produced very simple output:

{{EX1}}

As currently defined...

{{EX2}}

...if we wanted to include the animals' names in the printed lines. We'd have to go and manually update each 
Module's implementation, making changes to both `dog.claro` and `cat.claro` (and importantly, to any other animals we'd
want to share this updated behavior):

{{EX3}}

And now, after making the changes, rerunning will give us the updated output we were looking for:

{{EX4}}

Repetition may be ok in some situations, but in many others, it would represent a risk of potential maintenance costs.

Of course, you could always factor out the common logic into a new Module that can be depended on and called explicitly
by each animal implementation (and in fact, **this is absolutely the recommended approach in most situations**). But,
since we're interested in digging into possible Build time metaprogramming capabilities in this section, by way of
example, we'll walk through _**some other ways**_ you could go about _**sharing**_ this base implementation across
Modules that potentially wouldn't have been immediately obvious, coming from other languages.

## `claro_module(...)` Accepts Multiple Srcs

The first thing to understand is that a Module's implementation _**can be spread across multiple source files**_. This
means that different `.claro` files can satisfy different portions of a Module's API. And, more importantly for our
current purposes, this means that instead of creating a whole new Module to contain the common logic factored out of
`dog.claro` and `cat.claro`, we could instead define a single new file containing that factored out logic...

{{EX5}}

...include it in the `srcs` of **_BOTH_** Module declarations...

{{EX6}}

...and then directly call the factored out function in _each_ Module's implementation!

{{EX7}}

This is an example of **_LITERAL_** code reuse - something that's generally not actually possible in other languages.
In fact, you could take this a step further by factoring out this shared src file directly into the `Animal(...)` Macro
implementation to _automatically_ make the `getMessageWithName(...)` function available to **all** `Animal(...)`
declarations.

<div class="warning">

The key to this all working is that when the reused function references the `State` Type, it refers to either 
`Dog::State` or `Cat::State` depending on the context in which it's compiled. And the only field accessed via
`unwrap(state).name` is valid for both types. In a sense, this form of Build time metaprogramming has given this 
strongly, statically typed programming language, the ability to drop down into dynamic "duck typing" features when it's
convenient to us. This utterly blurs the lines between the two typing paradigms while still maintaining all of the
static type validations because all of this is happening at Build time, with Compile time's type-checking validations
still to follow!  
</div>

## "Inheritance" - Inverting the Prior Example

The prior example is a demonstration of the "composition" model where, in order to share code, you **_explicitly_** 
compose new code around the shared code by **_manually_** calling into the shared code. 

But, of course, while composition is generally recommended over the inverted "inheritance" model, many people prefer the
convenience that inheritance-based designs offer. Specifically, as you saw in the prior example, composition is more
verbose, as you have to **_explicitly_** opt in to code sharing, whereas inheritance makes this **_implicit_**.

Now, instead of each Module implementing the `AnimalSounds` Contract manually, a single default implementation can be
written...

{{EX8}}

...and then each Animal Module simply needs to define the expected internal implementation function `makeNoiseImpl(...)`
to provide its custom logic...

{{EX9}}

...and again, the "inherited" code can be included in the `srcs` of **_BOTH_** Module declarations...

{{EX10}}

<div class="warning">

Modern software engineering best practices have been progressing towards the consensus view that you should 
<a href="https://www.wikiwand.com/en/Composition_over_inheritance#Benefits" target="_blank">prefer composition over inheritance</a>.
But, even though this _preference_ is generally shared by Claro's author, it shouldn't necessarily indicate that
inheritance is impossible to achieve. While Claro won't ever add first-class language support for inheritance to the
language, Claro explicitly leaves these sorts of design decisions to you and provides Build time metaprogramming support
to allow the community to encode these sorts of organizational design patterns themselves to be available for whoever
decides they have a good reason for it. You shouldn't need to be hostage to the language designer's agreement or
prioritization to be able to extend the code organization patterns that can be expressed in the language.
</div>

## Further Flexibility

If you've made it this far, well done! You should now have the core conceptual background that you'll need to use
Bazel to encode your own relatively sophisticated organizational design patterns in your Claro programs using Build time
metaprogramming! 

Of course, there's always another step deeper into such waters. By continuing on to the next section, we'll continue to
develop the Animals example _even further_. In particular, we'll demonstrate one such sophisticated design pattern
called "Abstract Modules" that fully generalizes _all_ of the functionality described in the past two sections, and
goes _even further_ to provide significant configurability controls on top of what you've seen in the `Animal(...)`
macro so far.