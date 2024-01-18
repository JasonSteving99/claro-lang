# Known `copy(...)` Bugs

Currently Claro's copy implementation suffers from two different implementation problems that will be resolved in a
future release. I'll describe them below just for the sake of clarity.

<div class="warning">Please feel free to reach out if you'd like to help to address these bugs!</div>

## Compiler Stack Overflows on Copying Recursive Types

Currently, the Claro compiler doesn't do any special handling of recursively defined types, and so as it attempts to
generate code for an inlined copy of a recursive type, it ends up infinitely looping over the codegen phase.

{{EX1}}

In the future, this will be fixed by statically identifying when a recursive type is being copied, and then generating
a custom copy function for that particular type that will actually recurse **at runtime** rather than at compile time.
Note, this will put the onus on the programmer to ensure that they **never call `copy(...)` on any cyclical data 
structure**. 

## Generated Copy Logic Severs Shared References to Mutable Data

Potentially more nefarious than the previous bug, Claro's current copy implementation handles the copying of shared
references to mutable data in a way that is potentially likely to cause confusion or lead to bugs. A piece of nested
data that contains multiple fields of the same mutable type has the potential to contain shared references to the 
**same** mutable value. This is a semantically meaningful feature, not just some esoteric feature of the low-level 
memory layout. Mutation of this shared mutable data will be observable via each reference in the containing structure.
Problematically, when a copy is made, every single mutable value within the entire recursive structure will be 
guaranteed to have a single, unique reference. This may be a useful guarantee in some contexts, but I believe that this
goes against Claro's goals of being as unsurprising as possible. 

The copied data should have the exact same semantics as the original data that it was derived from, but in this one 
subtle way that is not currently the case. This will be fixed in a future release. 

{{EX2}}

## Mutability Coercion Can Circumvent a User Defined Type's `initializers` Restrictions

User Defined Types support the declaration of `initializers` that restrict the usage of the type's default constructor
to only the procedures defined within the `initializers` block. Claro's builtin `copy(...)` **currently provides a 
backdoor to initialize and instance of a user defined type without actually using one its initializers**.

This is fortunately of limited impact as the worst thing a user can do is create instances with a mutability declaration
that the type would otherwise not support. But regardless, this will be addressed in a future release.  

{{EX3}}
