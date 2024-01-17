# Known `copy(...)` Bugs

Currently Claro's copy implementation suffers from two different implementation problems that will be resolved in a
future release. I'll describe them below just for the sake of clarity:

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