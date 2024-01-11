# Contracts

Consider the example of the generic function:

{{EX1}}

If you really squint, you might notice that there's very little information available in the body of the
`filter<T>(...)` function to tell you about the type `T`. As a result, you're unable to do much with values of such an
unconstrained generic type beyond passing the value along to another generic function accepting an unconstrained generic
arg, *or* putting it into some collection defined over the same generic type. This would be very limiting if this was
all that could be done with generics.

__Enter Contracts!__ It will take a bit of a buildup, but we should be able to write generic functions that will be able
to put constraints on the acceptable types, for example saying something like "this procedure will accept any type, `T`,
for which the function `foo(arg1: T, arg2: T)` exists."

For example, we should be able to write the following generic function:

{{EX2}}

The function above has a new `requires(...)` clause in the signature which we haven't seen before. This is the mechanism
by which a function constrains the set of types that may be passed into this function to only types that definitely have
a certain associated procedure implementation existing. The `requires(...)` clause takes in a list of "Contracts" that
must be implemented over the generic type. In this case that contract's definition looks like:

{{EX3}}

This Contract specifies a single function signature that any implementation of this Contract must implement. Other
Contracts may specify more than one signature, or even more than one generic type param. There are no restrictions on
where the generic Contract param(s) may be used in the procedure signatures, so it may even be included in the return
type as shown in the example above.

The only requirement on signatures is that each one __must__ make use of __each__ generic arg type listed in the
Contract's signature. This is mandatory as Claro looks up the particular implementations by inspecting the arg types
provided at the Contract procedure's call-sites.

### Contracts are *Not* Interfaces

Coming from an Object-Oriented background, you may be tempted to compare Contracts to "Interfaces", but you'll find that
while they may be used to a similar effect, they are *not* the same thing. The intention of an "Interface" is to encode
subtyping relationships between types, whereas __Claro has absolutely no notion of subtyping__. All defined types are
strictly independent of one another. Claro asks you to simplify your mental model and simply think of Contracts as a
mechanism for encoding a required bit of functionality that needs to be implemented uniquely over values of unrelated,
arbitrary (generic) types.