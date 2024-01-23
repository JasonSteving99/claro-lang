# Error Handling

Claro takes a very principled stance that all control flow in the language should be modeled in a way that is 
self-consistent within the type system - as such, Claro chooses not to model errors around "throwing Exceptions". While 
many languages (e.g. Java/Python/C++/etc.) were designed around thrown exceptions as their error modeling tool, they all
suffer from the same antipattern that make it impossible to determine strictly from looking at a procedure signature
whether it's possible for the call to fail, and if so, what that failure might look like. This leads users into 
unnecessary digging to read implementation details to determine how and why certain unexpected error cases inevitably 
arise.

So, taking inspiration from many prior languages such as Rust, Haskell, and Go, Claro requires errors to be modeled
explicitly in procedures' signatures as possible return types so that all callers must necessarily either handle any 
potential errors, or explicitly ignore them or propagate them up the call stack.

## `std::Error<T>`

Claro's 
<a href="https://github.com/JasonSteving99/claro-lang/blob/main/stdlib/std.claro_module_api" target="_blank">std</a>
module exports the following type definition:

{{EX1}}

This type is a trivial wrapper around any arbitrary type. Its power is in the special treatment that the compiler gives
to this type to power Claro's error handling functionality. But first, let's take a look at how a procedure might make
use of this type to represent states in practice - the below example demonstrates a function that models safe indexing
into a list:

{{EX2}}

To drive the example home, instead of wrapping an atom which doesn't provide any information beyond the description of
the error itself, the error could wrap a type that contains more information:

{{EX3}}

**Continue on to the next section to learn about how Claro enables simple propagation of `std::Error<T>` values.**

