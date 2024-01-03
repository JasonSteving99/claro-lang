# Aliases are *Not* a New Type Declaration

It's important to know that, in general, defining an Alias does *not* declare a "new type", instead it is just providing
a shorthand for referring to some type. With an Alias definition, you are simply defining an alternative, more 
convenient way to refer to a type that is semantically equivalent to typing out the explicit type itself.

The example below demonstrates how variables with types declared using equivalent aliases, will in fact type-check as
having the same type:

{{EX1}}

## Note on "Nominal Typing"

Nominal typing can actually be very useful for enforcing maintenance of semantic interpretations of even simple data
types, and even for maintaining inter-field invariants of more complex structured data types. So, of course, Claro also 
provides a mechanism to define new, "nominally typed" type definitions. This will allow making a semantic distinction 
between two "structurally equivalent" types that have different names.

For more on this, see: [User Defined Types](../../user_defined_types/user_defined_types.generated_docs.md).
