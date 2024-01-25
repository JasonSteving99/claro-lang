# Dynamic Dispatch

"Dynamic Dispatch" is a fancy term for a call to an overloaded procedure (one with multiple implementations whose 
signatures differ only in the types of args/return value) being routed (a.k.a. "dispatched") to the appropriate
implementation **based on type information solely available at runtime**. 

## TLDR;

The short version of this section is that Claro supports the following:

{{EX1}}

_Feel free to ponder how this works. But keep reading if it's not immediately obvious what's going on here._

## By Comparison to Object-Oriented Programming

<div class="warning">
<b>This entire section is intended to build up your intuition for Claro's approach to Dynamic Dispatch by comparing and
contrasting with Java.</b>

[Skip ahead TODO(steving) Add link](#dynamic-dispatch) if you're already familiar with the concept of Dynamic Dispatch,
or keep reading for something of a deep dive.
</div>

Claro is truly a procedural language, and so is philosophically opposed to the personification of data that is a
fundamental property of "Object-Oriented" programming (OOP) languages like Java/Python/C++/etc. So, you won't find
anything resembling "Objects" or "Classes" in Claro. Additionally, Claro is philosophically opposed to the complexity of
inheritance, so again Claro's type system does not support it.

However, though Claro takes issue with the path OOP takes to achieve it, the paradigm provides some obviously useful 
abstractions that help programmers write very expressive code. Of particular interest in this section is the ability to 
write code that treats values of distinct types _interchangeably_ for the sake of dispatching to procedures that are 
known to be implemented over each of the distinct types in question.

In a language like Java, you'll accomplish this either by using _interfaces_, or by creating _subtype relationships 
between types using inheritance_.

### Using an Interface "Type" as a Procedure Arg (_in an OOP language_)

For example, the below **Java** code defines an interface with a single "method" that three classes implement.

{{EX2}}

And so a **Java** programmer can write a method that accepts an argument of type `Stringify`... but in **Java** parlance
any type that _implements_ the `Stringify` interface can be considered a **subtype** of `Stringify` and passed in its 
place:

{{EX3}}

This is a very convenient abstraction. However, in Java this **single** method implementation must handle multiple 
possible concrete subtypes of `Stringify` (in this case `Foo`, `Bar`, and `Buzz`). Java addresses this by dispatching to
the correct implementation of the `displayStr()` method **at runtime**, by dynamically checking the actual concrete type
of the object currently being handled. **This is already an example of Dynamic Dispatch. In Java, Dynamic Dispatch
is the norm**.

### Requiring a Contract to Be Implemented Over Generic Type Params (In Claro)

But subtyping is by no means essential for this to be possible. By now you've already seen that 
[Contracts](../contracts.generated_docs.md) provide a mechanism to express the same thing without resorting to creating
any subtyping relationships between types.

{{EX4}}

And additionally, as Claro's 
[generic procedures are "monomorphized"](../implementing_contracts/implementing_contracts.generated_docs.md#a-note-on-static-dispatch-via-monomorphization),
there is actually **no Dynamic Dispatch going on in the above example**. And when you stop and think about it, why would
there be? As a human looking at the three calls to `prettyPrint(...)`, there's zero uncertainty of the types in 
question. Unlike in the Java case, the Claro compiler actually takes advantage of this type information as well to 
generate code that **statically dispatches** to the correct implementations without requiring any runtime type checks.

### A (Not So) Brief Aside on the Limitations of Subtyping

You may be thinking that Java's use of subtyping makes the language simpler because it allows you to avoid the use of
Generics, but this is debatable at best. Consider a very slightly modified version of the above `prettyPrint()` function
that instead takes two arguments:

{{EX5}}

As it's currently defined, there's nothing requiring the two arguments to *actually* have the same type. In this trivial
example, that may be fine, but if I were to actually want to ensure that two arguments both implement an interface 
**_and_** they both actually have the same type, then I'm out of luck - **there's no way to statically encode this 
constraint in Java!**

In Claro, you would simply write:

{{EX6}}

And it will be a compilation error to pass arguments of different types:

{{EX7}}

But yet it will still be completely valid to pass arguments of the same type just like we wanted:

{{EX8}}

<div class="warning">

**HOT TAKE:** While Java's support for subtyping may **_seem_** like a powerful tool (and sometimes it really is 
convenient), it's actually explicitly **_taking away type information_**. You in fact end up with a 
**_less expressive_** language as a result of depending on subtyping.
</div>


# TODO(steving) FINISH THIS SECTION...NEED TO LAND THE PLANE AND ACTUALLY BUILD UP TO DYNAMIC DISPATCH IN CLARO