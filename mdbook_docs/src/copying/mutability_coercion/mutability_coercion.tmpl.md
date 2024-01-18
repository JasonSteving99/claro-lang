# Mutability Coercion on Copy

Claro's builtin `copy(...)` function supports the ability to _coerce_ the mutability of the data being copied. This is
primarily a matter of convenience to, in as many cases as possible, avoid Claro programmers to needing to manually write
custom copy implementations.

In order to convey that a mutability coercion is being requested, the return type of the `copy(...)` call simply needs 
to be constrained to some variant of the original value's type with mutability annotations updated as desired. Claro 
will automatically codegen the appropriate logic to perform the requested copying + coercion. Note that this feature
relies on compile-time knowledge to ensure that any coercions would not actually invalidate any language semantics or
violate type system rules.

In the below example, a `mut [[int]]` is copied, with the type simultaneously coerced to `[mut [int]]`: 

{{EX1}}

## Mutability Coercion Can Apply to Type Parameters of a User Defined Type

It's worth noting explicitly that Claro's `newtype` declarations statically encode the mutability any collections they
happen to wrap. Claro's builtin `copy(...)` **cannot** be used to invalidate these **explicit** mutability declarations,
for example:

{{EX2}}

However, parameterized User Defined Types may accept any concrete type in the place of the generic type parameter, and
Claro's builtin `copy(...)` function *can* be used to do mutability coercion on these values. 

The below example demonstrates setting the concrete type `T = mut tuple<string, int>` meaning that `Foo<T>` originally 
wraps the type `[mut tuple<string, int>]`. Then, upon copying the original value, the type is coerced to 
`T = tuple<string, int>` resulting in `Foo<T>` wrapping the deeply immutable type `[tuple<string, int>]`: 

{{EX3}}