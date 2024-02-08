# Type & Alias Definitions

Modules can also be used to export definitions of Types or Aliases that are intended to be used throughout your overall
program. 

## Exporting Type Definitions

Exporting a Type definition can be as simple as just using the same Type definition syntax that you'd use within a
`.claro` source file.

For example, the [std module](../../../stdlib/std_module.generated_docs.md) exports the following Type:

{{EX1}}

<div class="warning">
The one thing to keep in mind when exporting a Type definition using this basic syntax is that you're explicitly giving
consumers access to the internal details of the type itself. This has the potential to make for a very unstable API as
any consumers can freely unwrap values of the type and manipulate its underlying representation at will. This is 
obviously unsafe if the Type happens to be mutable as downstream consumers may not know how to maintain any inter-field
invariants if they're allowed to mutate it at will. 

And beyond mutability, perhaps more subtly, you should also consider whether there will be lots of downstream users 
directly accessing the Type's internal representation, and if so whether the representation is ever subject to any 
future change. If so, in the future, it may unknowingly become very hard to ever make changes to the Type's internal 
representation as, to do so, you would simultaneously be forced to update all of the downstream references to the Type's
internal representation.

Thankfully, Claro actually has mechanisms to hide the internal representation of a Type definition from downstream 
consumers. Learn more in the sections on 
[Unwrappers](./initializers_and_unwrappers/initializers_and_unwrappers.generated_docs.md) and 
[Opaque Types](./opaque_types/opaque_types.generated_docs.md).
</div>

## Exporting Atoms

Exporting an Atom is something of a hybrid between exporting a 
[static value](../static_values/static_values.generated_docs.md) and a Type definition, as an atom defines a new type 
whose only value is the Atom itself. But again, you may export Atoms from Module APIs exactly as it would be defined 
within a `.claro` source file.

For example, the [strings module](../../../stdlib/strings_module.generated_docs.md) exports the following atom and
several functions that reference it.

{{EX2}}

## Exporting Aliases

While Aliases largely exist to allow you to create your own convenient syntax sugar for complex types, it can sometimes
be useful for a Module to provide a standardized Alias for long or complex types that downstream usages could benefit
from having a shorthand for. Syntax for exporting an Alias in a Module API is exactly the same as the syntax for
declaring an Alias in a `.claro` source file.

{{EX3}}

## Modules Exporting **Only** Types/Aliases Don't Require any `.claro` Source Files

In general, if your Module **exclusively** exports Type or Alias definitions, you actually do not need to provide any
`.claro` srcs to the defining `claro_module(...)` target, as the definitions themselves fully specify the Module in 
their own right.

{{EX4}}

{{EX5}}