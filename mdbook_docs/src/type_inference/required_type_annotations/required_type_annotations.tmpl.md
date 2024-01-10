# Required Type Annotations

There are same specific situations where Claro will require a type annotation to understand your intent. Note that these 
situations are not just a limitation of the compiler, even if Claro would somehow implicitly decide a type for you in 
these situations, your colleagues (or your future self) would struggle to comprehend what type was being inferred.

For clarity and correctness in the following situations, you will be required to write an explicit type annotation:

### Procedure Signatures

Most obvious is the fact that all [procedure](../../procedures.md) signatures must fully encode the types of any 
arguments and, if the procedure returns a value, its return type.

{{EX1}}

<div class="warning">

If you're thinking, _"but sometimes I want to write procedures that can accept values of more than one type!"_, then you
have a couple options:

- If you know the possible set of types ahead of time: use 
[`oneof<...>`](../../static_typing/oneofs/oneofs.generated_docs.md)
- Otherwise: use [generics](../../generics.generated_docs.md)
</div>

### Lambda Expressions assigned to variables

As lambdas are just anonymous procedures, they must either be used in a context that already "asserts" the lambda's
signature, such as in this variable declaration:

{{EX2}}

Note: Claro does support an alternative syntax sugar to bake the type annotation directly into the lambda expression:

{{EX3}}

### Initializing Empty Builtin Collections

Claro would have no way of knowing what type the below list was intended to be without an explicit type annotation:

{{EX4}}

### Non-literal Tuple Subscript

Unlike with literal integer tuple subscript indices, when you use a non-literal tuple subscript index value, you have
hidden the index from Claro's type inference behind a layer of indirection that Claro will not attempt to follow. In
these cases you'll be required to assert your intent via a runtime type cast:

{{EX5}}

<div class="warning">

**Warning**: Claro allows this simply to avoid being too restrictive, but you should arguably take these runtime casts
as a code-smell and find a statically safe way to rewrite your code to avoid this sort of dynamic tuple subscripting.
</div>

### (Advanced) Calls to Generic Procedure With Unconstrained Return Type

When a generic return type can't be inferred from arg(s) referencing the same generic type, you must explicitly assert
the type that you intend for the procedure to return. 

This is something that will likely only come up in more advanced usage of the language. Getting into this situation 
requires using multiple language features together in a rather intentional way, but for completeness here's an example 
of how this may happen:

{{EX6}}

### Any Ambiguously-Typed Expression Passed to a Generic Function Arg Position

Because Claro monomorphizes [generic procedures](../../generics.generated_docs.md), Claro must determine the called 
procedure's concrete types based on the types of the arguments. In the case that the type of an argument expression is
ambiguous, it must be explicitly annotated with a cast:

{{EX7}}

However, the effect of this can be limited in generic procedures with multiple arguments. The type cast may not be 
necessary if the type parameter is already constrained by another preceding argument:

{{EX8}}