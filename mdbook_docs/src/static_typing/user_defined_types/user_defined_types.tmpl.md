# User Defined Types

Claro's type system already provides a very expansive expressive power to represent arbitrarily complex data structures,
so, technically speaking, there is no hard _requirement_ for a user to ever define any new types in order write any
program. However, by using only the builtin primitive and collection types, you will not be able to leverage Claro's
static type validation to ensure that semantic differences between values with structurally equivalent types are 
actually maintained.

This section will attempt to clarify how you can make use of user defined types to enforce semantic constraints 
throughout your program.

## Declaring a New Type

The example below demonstrates the declaration of a new type that **wraps** `int`.

{{EX1}}

## Instantiating an Instance of a User Defined Type

Claro automatically provides a one-arg constructor that allows the user defined type to be instantiated by wrapping the
declared type.

{{EX2}}

## User Defined Types "Wrap" an Instance of Another Type

Because Claro's builtin types already enable modelling any arbitrary data structure, the purpose of user defined types
is solely to "wrap" an existing type in a statically enforceable, semantic layer that distinguishes instances of the
user defined type, from the type that is being wrapped. As such, Claro does not do any automatic conversions from the
wrapped type to the unwrapped type.

So, although `newtype Foo : int` simply wraps `int`, it is not interchangeable with `int` and therefore operations like
`+` are not supported for `Foo` even though they are for `int`.

{{EX3}}

## "Unwrapping" a User Defined Type

The wrapped type can be accessed by explicitly using the builtin `unwrap()` function.

{{EX4}}

## Compile Time Enforcement

In the [Aliases section an example was given that demonstrates the pitfall of the overuse of aliases](../aliases/aliases.generated_docs.md#overuse-of-aliases-can-be-a-code-smell).
One primary source of errors could be addressed by simply declaring a new type for each of `MPH`, `Hours`, and `Miles`.
In this case, this statically prevents accidentally passing args to the function out of order:

{{EX5}}

The above error message would lead you to correct the order of arguments and thereby fix the problem:

{{EX6}}
