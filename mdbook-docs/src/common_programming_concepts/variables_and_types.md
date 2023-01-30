# Variables & Primitive Types

Claro is a statically-compiled, strictly typed language. Practically speaking, this means that the type of all variables
must be statically determined upon declaration of the variable, and may never change thereafter.

Claro has a few builtin "primitive" types representing generally small or low-level "value types" that are immutable to
the programmer. They are referred to as "primitive" because they are foundational to the language's type system, and
make up the basic building blocks of which every other type in the language is just some structured combination. Values
of these primitive types are generally cheap to allocate on the stack, and are passed as copies to other functions
(strings, being handled in typical JVM fashion, are actually heap allocated with references to strings passed instead of
copying the value itself).

More are coming soon, but for now the supported set of primitives include: int, float, boolean, string. The example
below shows how you'd define variables to represent values of each type:

```
# All immutable.
var i: int = 10; # Any whole number. 
var f: float = 1.15; # Any decimal number.
var b: boolean = true; # true or false.
var s: string = "very first string"; # Any sequence of chars. Heap allocated.
```

To break the syntax down further:

`var` : Keyword introducing / declaring a new variable.

`b` : the name we chose for this particular var.

`:` : a syntactic divider between a variable's name and its type.

`boolean` : the type of the variable, which constrains the domain of values which this variable may hold. 
