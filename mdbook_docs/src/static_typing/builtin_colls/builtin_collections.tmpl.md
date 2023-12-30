# Builtin Collections

Claro also rounds out its builtin types with a small set of convenient collection types that allow you to manipulate
many values using a single variable. These are provided as builtins for your convenience, but their implementations have
been hand selected to cover the majority of your general purpose programming use cases.

## Mutability

All of Claro's builtin collection types come in either a **mutable** or (shallowly) **immutable** variant - by default, 
Claro will assume that any collection literals are intended to be __immutable__. 

{{EX1}}

The following example demonstrates initialization of a **mutable** list of integers:
{{EX2}}

## Mutability Annotations are Shallow

Claro's mutability annotations are shallow by design so that you maintain fine-grained control over creating arbitrarily
complex nested data structures that mix mutability and immutability as needed. The following examples demonstrate 
different combinations of nested mutability annotations:

This example demonstrates a mutable list whose elements are immutable lists.
{{EX3}}

This example demonstrates an immutable list whose elements are mutable lists.
{{EX4}}

## Data Race Safety via Deep Immutability

<div class="warning">
This builtin support for mutability annotations allows Claro to enforce some very strong safety guarantees in concurrent 
contexts, so this is about more than just providing a convenient library of data types.
</div>

See the [Concurrency](../concurrency.md) section in this book for more details on how Claro will statically leverage 
knowledge of whether a type is deeply immutable or not to prevent unsafe data races.