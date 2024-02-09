# Initializers & Unwrappers

Claro's builtin types are already fully capable of expressing _any_ data structure, and so the entire purpose of 
User-Defined Types is to allow user code to overlay _new semantic meaning_ onto types beyond just the raw data values
themselves. Claro supports two simple constructs that allow User-Defined Types to constrain, and reinterpret the raw
data types that they wrap. Note that both of these constructs should likely only be used in limited cases where you have
a very specific reason to be doing so.

## Initializers

Initializers provide a mechanism for a User-Defined Type to constrain the domain of possible values that a type may
represent beyond what the raw data types imply on their own.

To demonstrate the problem being addressed, take for example the type declaration below:

{{EX1}}

There's nothing about the type definition alone that _actually_ imposes any sort of constraint that actually guarantees
that the wrapped int is in fact odd. So a consumer could place a dep (`Nums`) on the Module and directly construct a
completely invalid instance of the `OddInt` type:

{{EX2}}

{{EX3}}

Of course, it'd be very much preferable for it to be impossible to ever construct an instance of a Type that violates
its semantic invariants. You can enforce this in Claro by defining **Initializers** over the Type. Initializers are
simply procedures that become the **only procedures in the entire program that are allowed to directly use the Type's
constructor**. Therefore, if a Type declares an `initializers` block, the procedures declared within become the **only**
way for anyone to receive an instance of the type.

{{EX4}}

{{EX5}}

Now, the exact same attempt to construct an invalid instance of `OddInt` is statically rejected at compile-time - and
even better, Claro's able to specifically recommend the fix, calling the `Nums::getOddInt(...)` function:

{{EX6}}

<div class="warning">

_Note: Claro's error messaging is a work in progress - the above error message will be improved._
</div>

And now finally, you can use the initializer by simply calling it like any other procedure:

{{EX7}}

Now you know for a fact that anywhere where you initialize an instance of an `OddInt` in the entire program, it will
certainly satisfy its semantic invariants. 

<div class="warning">

**Warning**: Still, keep in mind that if your type is mutable, declaring Initializers is not, on its own, sufficient to
guarantee that any constraints or invariants are maintained over time. Keep reading to learn about how Unwrappers and
Opaque Types can give you full control over this.
</div>
