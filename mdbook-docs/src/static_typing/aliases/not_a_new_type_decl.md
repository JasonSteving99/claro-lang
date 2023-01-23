# Aliases are *Not* a New Type Declaration

It's important to know that, in general, defining an Alias does *not* declare a "new type", instead it is just providing
a short-hand for referring to some type. For simple (non-recursive) Alias definitions, you are simply defining a new,
more convenient way to refer to a type that is equivalent to typing out the long-form of the type.

The example below demonstrates how variables with types declared using equivalent aliases, will in fact type-check as
having the same type:

```
alias IntList1 : [int]
alias IntList2 : [int]

var i1: IntList1 = [1];
var i2: IntList2 = [2];
i1 = i2;                    # IntList1 is equivalent to IntList2.

var iLiteral: [int] = [3];
i2 = iLiteral;              # IntList2 is equivalent to [int].
```

# Aliases as "Structural Typing"

Claro's Aliases are a mechanism to define values with "structural types". Roughly speaking, this is why any value
defined defined with a "structurally equivalent" alias is considered to be interchangeable with any other value,
regardless of the originally used alias in the variable's declaration. Many languages use a different form of typing
known as "nominal typing" which implies that the *name* of the type is the thing that determines equivalence, rather
than the structure, but this is not the case with aliases in Claro.

# Note on "Nominal Typing"

Nominal typing can actually be very useful for enforcing maintenance of inter-field invariants in structured data, so,
in the future Claro will provide a mechanism to define new, "nominally typed" type definitions that will allow making a
distinction between two "structurally equivalent" types, that have different names.

This will allow you to confindently ensure that data with a certain type that semantically has inter-field invariants
that need to be maintained across mutations are not semantically invalidated by passing the data off to a mutating
procedure that wasn't implemented with the nominal type's invariants in mind. This semantic data invalidation would be
easy to run into if the only type validation scheme available was structural typing, as that validation scheme makes
type equivalence decisions with zero semantic knowledge.