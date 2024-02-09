# Unwrappers

Initializers are a very useful concept, but on their own they don't allow full control over maintaining a mutable type's
semantic invariants or constraints. For example, consider the following type definition: 

{{EX1}}

If we wanted to impose the semantic constraint on legal values for a `Person`'s `age`, defining the initializer alone is
only sufficient to ensure the constraint is enforced for the initial value. But it doesn't help maintain this after init
as users could still freely unwrap and mutate the type directly:

{{EX2}}

Fortunately, Claro provides a couple different ways to actually control semantic constraints/invariants like this. The
first approach is to define Unwrappers. Analogous to Initializers that constrain the usage of a Type's default
constructor, Unwrappers constrain the usage of the built-in `unwrap(...)` operation. For example, the above violation of
the intended constraints on a `Person`'s `age` can be enforced by adding an Unwrapper procedure that will handle all
allowed updates:

{{EX3}}

And now, the workaround that previously allowed violating the type's constraints has been patched. Attempts to directly
mutate the value w/o going through approved procedures that handle updates will be rejected at compile-time:

{{EX4}}

Now, if you actually tried to update the age to something invalid using the official `setAge(...)` function, the update
will be rejected:

{{EX5}}

## Recommended Use of Unwrappers and Initializers

<div class="warning">

It's worth noting that `initializers` and `unwrappers` blocks exist largely to be used **independently**. The above
example is fairly contrived, and would likely be better defined as an 
["Opaque Type"](../../opaque_types/opaque_types.generated_docs.md). A good rule of thumb is that if you catch yourself
thinking that you need to define both for the same Type, you should likely be defining the Type to be "Opaque" instead.

In particular, `initializers` can be well-used in isolation for immutable Types where you would like to validate the
values on init, but would like to maintain the ergonomics of allowing users to directly access the internals themselves
(and as the data is immutable, there's no risk in allowing them to do so).
For example, with the immutable type `newtype GameLocation : struct {x: int, y: int}` you may want to require that `x`
and `y` are _actually_ within the game's boundaries, but otherwise you want to allow users of the type to directly
access `x` and `y` without having to write/use annoying "getters".

On the other hand, `unwrappers` can be well-used in isolation for mutable values that can _start_ with any value, but
for which all subsequent changes must be constrained. For example, with
`newtype MonotonicallyIncreasingValue: mut struct {val: long}` you may be happy to allow arbitrary _starting_ values,
but after that point you would want to ensure that any updates to its value are in fact increasing its value, perhaps by
simply exposing an Unwrapper like `consumer increment(count: MonotonicallyIncreasingValue);`.
</div>